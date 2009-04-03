// Copyright 2002-2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.jos;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamConstants;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.joe_e.Immutable;
import org.joe_e.JoeE;
import org.joe_e.Struct;
import org.joe_e.array.ByteArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.charset.URLEncoding;
import org.joe_e.file.InvalidFilenameException;
import org.joe_e.reflect.Reflection;
import org.joe_e.var.Milestone;
import org.ref_send.log.Event;
import org.ref_send.promise.Eventual;
import org.ref_send.promise.Log;
import org.ref_send.promise.Promise;
import org.ref_send.promise.Receiver;
import org.ref_send.promise.Rejected;
import org.waterken.base32.Base32;
import org.waterken.cache.CacheReference;
import org.waterken.db.Creator;
import org.waterken.db.CyclicGraph;
import org.waterken.db.Database;
import org.waterken.db.Effect;
import org.waterken.db.ProhibitedCreation;
import org.waterken.db.ProhibitedModification;
import org.waterken.db.Root;
import org.waterken.db.Service;
import org.waterken.db.Transaction;
import org.waterken.db.TransactionMonitor;
import org.waterken.project.Project;
import org.waterken.store.Store;
import org.waterken.store.Update;
import org.waterken.trace.EventSender;
import org.waterken.trace.Tracer;
import org.waterken.trace.TurnCounter;
import org.waterken.trace.application.ApplicationTracer;

/**
 * An object graph stored as a set of Java Object Serialization files.
 */
/* package */ final class
JODB<S> extends Database<S> {
    
    /**
     * Canonicalizes a {@link Root} name.
     * <p>
     * All lower-case names are used to hide differences in how different file
     * systems handle case-sensitivity.
     * </p>
     * @param name chosen name
     * @return canonical name
     */
    static protected String
    canonicalize(final String name) { return name.toLowerCase(Locale.ENGLISH); }

    /**
     * file extension for a serialized Java object tree
     */
    static protected final String ext = ".jos";

    static private final int keyChars = 70 / 5;     // 70 bits > 10^21 keys
    static private final int keyBytes = keyChars * 5 / 8 + 1;

    /**
     * Turns an object identifier into a filename.
     * @param id    object identifier
     * @return corresponding filename
     */
    static private String
    filename(final byte[] id) {return Base32.encode(id).substring(0, keyChars);}

    private final Receiver<Event> stderr;   // log event output
    private final Store store;              // byte storage

    protected
    JODB(final S session, final Receiver<Service> service,
         final Receiver<Event> stderr, final Store store) {
        super(session, service);
        this.stderr = stderr;
        this.store = store;
    }
    
    static private <S> Receiver<Object>
    makeDestructor(final Receiver<Effect<S>> effect) {
        class Destruct extends Struct implements Receiver<Object>, Serializable{
            static private final long serialVersionUID = 1L;
            
            public void
            run(final Object ignored) {
                effect.run(new Effect<S>() {
                    public void
                    run(final Database<S> origin) {
                        while (true) {
                            try {
                                ((JODB<?>)origin).store.clean();
                                break;
                            } catch (final Exception e) {
                                try {
                                    Thread.sleep(1000);
                                } catch (final InterruptedException e1) {}
                            }
                        }
                    }
                });
            }
        }
        return new Destruct();
    }

    // org.waterken.db.Database interface
    
    private String project;
    private ClassLoader code = JODB.class.getClassLoader();
    
    public String
    getProject() throws Exception {
        synchronized (store) {
            wake();
            return project;
        }
    }

    public <R extends Immutable> Promise<R>
    enter(final boolean isQuery, final Transaction<R> body) throws Exception {
        synchronized (store) {
            wake();
            return process(isQuery, body);
        }
    }
    
    /**
     * Has the {@link #wake wake} task been run?
     */
    private final Milestone<Boolean> awake = Milestone.plan();
    
    private void
    wake() throws Exception {
        if (!awake.is()) {
            process(Transaction.query, new Transaction<Immutable>() {
                public Immutable
                run(final Root root) throws Exception {
                    final Receiver<?> wake = root.fetch(null, Database.wake);
                    if (null != wake) { wake.run(null); }
                    return new Immutable() {};
                }
            }).call();
            awake.mark(true);
        }
    }
    
    // org.waterken.jos.JODB interface
    
    /**
     * An object store entry.
     */
    static private final class
    Bucket extends Struct {
        final CacheReference<String,Object> value;
        final boolean created;      // Is this a newly created bucket?
        final ByteArray version;    // secure hash of value, or
                                    // <code>null</code> if not known
        final boolean managed;      // Does value contain only managed state?
        final PowerlessArray<String> splices;   // buckets spliced into value

        Bucket(final CacheReference<String,Object> value,
               final boolean created, final ByteArray version,
               final boolean managed, final PowerlessArray<String> splices) {
            if (null == value) { throw new AssertionError(); }
            if (!created && null == version) { throw new AssertionError(); }
            if (!created && null == splices) { throw new AssertionError(); }
            
            this.value = value;
            this.created = created;
            this.version = version;
            this.managed = managed;
            this.splices = splices;
        }
    }

    private       SecureRandom prng;
    private final ReferenceQueue<Object> wiped = new ReferenceQueue<Object>();
    private final HashMap<String,Bucket> f2b = new HashMap<String,Bucket>(64);
    private       Processor tx;     // currently active transaction processor
    
    static private final class
    Processor {
        final boolean isQuery;
        final Update update;
        final IdentityHashMap<Object,String> o2f;  // [ object => filename ]
        final IdentityHashMap<Object,String> o2wf; // [ object => weak filename]
        final HashSet<String> xxx;                 // [ dirty filename ]
        final LinkedList<Service> services = new LinkedList<Service>();
        final LinkedList<Event> events = new LinkedList<Event>();
        
        Processor(final boolean isQuery, final Update update) {
            this.isQuery = isQuery;
            this.update = update;
            o2f = new IdentityHashMap<Object,String>(32);
            o2wf = new IdentityHashMap<Object,String>(32);
            xxx = new HashSet<String>(16);
        }
    }
    
    private void
    create(final String f, final Object o, final ByteArray version) {
        f2b.put(f, new Bucket(new CacheReference<String,Object>(f, o, wiped),
                              true, version, false, null));
        tx.o2f.put(o, f);
        tx.xxx.add(f);
    }
    
    private final Root root = new Root() {
        
        private boolean
        exists(final String f) {
            try {
                return f2b.containsKey(f) || tx.update.includes(f + ext);
            } catch (final Exception e) { throw new Error(e); }
        }

        private final ArrayList<String> stack = new ArrayList<String>(16);

        /**
         * Gets the corresponding value, loading from the store if needed.
         * @param f name of corresponding bucket
         * @return corresponding value
         * @throws FileNotFoundException    no corresponding value
         * @throws RuntimeException         syntax problem with state
         * @throws Error                    I/O problem
         */
        private Object
        load(final String f) throws FileNotFoundException, RuntimeException {
            if ("".equals(f)) { return null; }
            
            {
                // check the cache
                final Bucket b = f2b.get(f);
                final Object o = null != b ? b.value.get() : null;
                if (null != o) {
                    markDirty(f, o, b);
                    return o;
                }
            }

            // Wipe old entries.
            while (true) {
                final Reference<?> r = wiped.poll();
                if (null == r) { break; }
                f2b.remove(((CacheReference<?,?>)r).key);
            }
            
            final int startCycle = stack.lastIndexOf(f);
            if (-1 != startCycle) {
                PowerlessArray<String> cycle =
                    PowerlessArray.array(new String[] {});
                for (final String at : stack.subList(startCycle,stack.size())) {
                    try {
                        cycle = cycle.with(identify(tx.update.read(at + ext)));
                    } catch (final IOException e) { throw new Error(e); }
                }
                throw new CyclicGraph(cycle.with(cycle.get(0)));
            }
            
            InputStream in;
            try {
                in = tx.update.read(f + ext);
            } catch (final FileNotFoundException e) {
                throw e;
            } catch (final IOException e) {
                throw new Error(e);
            }
            stack.add(f);
            try {
                final Object o;
                final ByteArray version;
                final Milestone<Boolean> unmanaged = Milestone.plan();
                final HashSet<String> splices = new HashSet<String>(8);
                if (canonicalize(JODB.secret).equals(f)) {
                    // base case for loading the master secret
                    final ObjectInputStream oin = new ObjectInputStream(in);
                    in = oin;
                    o = oin.readObject();
                    version = ByteArray.array();
                } else {
                    final Mac mac = allocMac(this);
                    in = new MacInputStream(mac, in);
                    final SubstitutionStream oin =
                            new SubstitutionStream(true, code, in) {
                        protected Object
                        resolveObject(Object x) throws IOException {
                            if (x instanceof File) {
                                unmanaged.mark(true);
                            } else if (x instanceof Splice) {
                                splices.add(((Splice)x).name);
                                x = load(((Splice)x).name);
                            }
                            return x;
                        }
                    };
                    in = oin;
                    o = oin.readObject();
                    version = ByteArray.array(mac.doFinal());
                    freeMac(mac);
                }
                f2b.put(f, new Bucket(
                    new CacheReference<String,Object>(f, o, wiped),
                    false, version, !unmanaged.is(), 
                    PowerlessArray.array(splices.toArray(new String[0]))));
                tx.o2f.put(o, f);
                if (!JoeE.isFrozen(o)) { tx.xxx.add(f); }
                return o;
            } catch (final InvalidClassException e) {
                throw new RuntimeException(e);
            } catch (final ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (final IOException e) {
                throw new Error(e);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            } finally {
                stack.remove(stack.size() - 1);
                try { in.close(); } catch (final Exception e) {}
            }
        }
        
        private void
        markDirty(final String f, final Object o, final Bucket b) {
            if (!b.created && null == tx.o2f.put(o, f)) {
                if (!JoeE.isFrozen(o)) { tx.xxx.add(f); }
                for (final String splice : b.splices) {
                    final Bucket spliced = f2b.get(splice);
                    markDirty(splice, spliced.value.get(), spliced);
                }
            }
        }
        
        // org.waterken.db.Root interface
        
        /**
         * Creates an object store entry.
         * <p>
         * Each entry is stored as a binding in the persistent store. The
         * corresponding filename is of the form XYZ.jos, where the XYZ
         * component may be chosen by the client and the ".jos" extension is
         * automatically appended by the implementation. All filenames are
         * composed of only lower case letters. For example,
         * ".stuff.jos.jos" is the filename corresponding to the user chosen
         * name ".Stuff.JOS". The file extension chosen by the user carries
         * no significance in this implementation, and neither does a
         * filename starting with a "." character.
         * </p>
         * <p>
         * The name generated by {@link #export} is considered the
         * canonical name for an object. Names assigned to an object via
         * {@link #link} are valid for lookup, but will not be returned by
         * {@link #export}. This feature is implemented by wrapping a
         * {@linkplain #link linked} object in a {@link SymbolicLink}, and
         * unwrapping it on {@link #fetch}.
         * </p>
         */
        public void
        link(final String name, final Object value) {
            if (tx.isQuery) {
              throw new ProhibitedModification(Reflection.getName(Root.class));
            }

            final String filename = canonicalize(name);
            if (exists(filename)) { throw new InvalidFilenameException(); }
            create(filename, new SymbolicLink(value), null);
        }

        public @SuppressWarnings("unchecked") <T> T
        fetch(final Object otherwise, final String name) {
            try {
                final Object value = load(canonicalize(name));
                return (T)(value instanceof SymbolicLink
                    ? ((SymbolicLink)value).target : value);
            } catch (final FileNotFoundException e) { return (T)otherwise; }
        }

        public String
        export(final Object o, final boolean isWeak) {
            String f = tx.o2f.get(o);
            if (null != f) { return f; }
            if (null == o || Slicer.inline(o.getClass())) {
                // reuse an equivalent persistent identity;
                // otherwise, determine the persistent identity
                final byte[] rawVersion;
                try {
                    final Mac mac = allocMac(this);
                    final Slicer out =
                        new Slicer(o, this, new MacOutputStream(mac, null));
                    out.writeObject(o);
                    out.flush();
                    out.close();
                    rawVersion = mac.doFinal();
                    freeMac(mac);
                } catch (final Exception e) { throw new Error(e); }
                final ByteArray version = ByteArray.array(rawVersion);
                final byte[] id = new byte[keyBytes];
                System.arraycopy(rawVersion, 0, id, 0, id.length);
                while (true) {
                    f = filename(id);
                    try {
                        if (!exists(f)) {
                            create(f, o, version);
                            break;
                        }
                        load(f);
                        if (version.equals(f2b.get(f).version)) { break; }
                    } catch (final Exception e) { /*skip broken bucket*/ }
                    for (int i = 0; i != id.length && 0 == ++id[i]; ++i) {}
                }
                return f;
            }
            
            // to support caching of query responses, forbid
            // export of selfish state in a query transaction
            if (tx.isQuery) {
                throw new ProhibitedCreation(Reflection.getName(o.getClass()));
            }
            
            // check for an existing weak identity
            f = tx.o2wf.get(o);
            if (null == f) {
                // create a new identity
                do {
                    final byte[] id = new byte[keyBytes];
                    prng.nextBytes(id);
                    f = filename(id);
                } while (exists(f));
            }
            
            // assign the identity
            if (isWeak) {
                tx.o2wf.put(o, f);
            } else {
                create(f, o, null);
                tx.o2wf.remove(o);
            }
            return f;
        }
    };
    final TransactionMonitor monitor = new TransactionMonitor() {
        public String
        tag() {
            final Mac mac;
            try {
                mac = allocMac(root);
            } catch (final Exception e) { throw new Error(e); }
            for (final String name : tx.o2f.values()) {
                final Bucket b = f2b.get(name);
                if (!b.created) {
                    if (!b.managed) { return null; }
                    mac.update(b.version.toByteArray());
                }
            }
            if (code instanceof Project) {
                // include code timestamp in the ETag
                final long buffer = ((Project)code).timestamp;
                for (int i = Long.SIZE; i != 0;) {
                    i -= Byte.SIZE;
                    mac.update((byte)(buffer >>> i));
                }
            }
            final byte[] id = mac.doFinal();
            freeMac(mac);
            return '\"' + Base32.encode(id).substring(0, 2*keyChars) + '\"';
        }
    };
    final Receiver<Effect<S>> effect = new Receiver<Effect<S>>() {
        public void
        run(final Effect<S> task) {
            tx.services.add(new Service() {
                public Void
                call() throws Exception {
                    task.run(JODB.this);
                    return null;
                }
            });
        }
    };
    final Creator creator = new Creator() {
        public <X extends Immutable> Promise<X>
        run(final String project, final String base, String name,
            final Transaction<X> setup) throws InvalidFilenameException,
                                               ProhibitedModification {
            if (tx.isQuery) {
                throw new ProhibitedModification(
                        Reflection.getName(Creator.class));
            }

            Store subStore;
            if (null != name) {
                name = canonicalize(name);
                try {
                    subStore = tx.update.nest(name);
                } catch (final InvalidFilenameException e) {
                    throw e;
                } catch (final Exception e) { throw new Error(e); }
            } else {
                while (true) {
                    try {
                        final byte[] d = new byte[4];
                        prng.nextBytes(d);
                        name = Base32.encode(d).substring(0, 6);
                        subStore = tx.update.nest(name);
                        break;
                    } catch (final InvalidFilenameException e) {
                    } catch (final Exception e) { throw new Error(e); }
                }
            }
            try {
                final String here = base + URLEncoding.encode(name) + "/";
                final byte[] bits = new byte[128 / Byte.SIZE];
                prng.nextBytes(bits);
                final ByteArray secretBits = ByteArray.array(bits);
                final JODB<S> sub = new JODB<S>(null, null,
                    null == stderr ? new Receiver<Event>() {
                        public void
                        run(final Event value) {}
                } : stderr, subStore);
                sub.project = null != project ? project : JODB.this.project;
                sub.code = Project.connect(sub.project);
                return sub.process(Transaction.update, new Transaction<X>() {
                    public X
                    run(final Root local) throws Exception {
                        local.link(Database.project, sub.project);
                        local.link(Database.here, here);
                        local.link(secret, secretBits);
                        final TurnCounter turn = TurnCounter.make(here);
                        local.link(JODB.flip, turn.flip);
                        final ClassLoader code =
                            local.fetch(null, Database.code);
                        final Tracer tracer = ApplicationTracer.make(code);
                        final Receiver<Effect<S>> effect =
                            local.fetch(null, Database.effect);
                        local.link(Database.destruct,
                                   makeDestructor(effect));
                        sub.create(canonicalize(Database.log), EventSender.make(
                                        sub.txerr, turn.mark, tracer), null);
                        return setup.run(local);
                    }
                });
            } catch (final Exception e) { throw new Error(e); }
        }
    };
    final Receiver<Event> txerr = new Receiver<Event>() {
        public void
        run(final Event event) { tx.events.add(event); }
    };
    final Log nop = new Log();

    protected <R extends Immutable> Promise<R>
    process(final boolean isQuery, final Transaction<R> body) throws Exception {
        Promise<R> r;
        final Processor m = tx = new Processor(isQuery, store.update());
        try {
            // finish Vat initialization, which was delayed to avoid doing
            // anything intensive while holding the global "live" lock
            final boolean restockCache = f2b.isEmpty();
            if (null == prng) { prng = new SecureRandom(); }
            if (null == project) {
                try {
                    project = root.fetch(null, Database.project);
                } catch (final Exception e) { throw new Error(e); }
                code = Project.connect(project);
            }
            
            // setup the pseudo-persistent objects
            m.o2f.put(code,         canonicalize(Database.code));
            m.o2f.put(creator,      canonicalize(Database.creator));
            m.o2f.put(effect,       canonicalize(Database.effect));
            m.o2f.put(null,         canonicalize(Database.nothing));
            m.o2f.put(monitor,      canonicalize(Database.monitor));
            m.o2f.put(txerr,        canonicalize(".txerr"));
            m.o2f.put(root,         canonicalize(".root"));
            if (null == stderr) {
                // short-circuit the log implementation
                m.o2f.put(nop,      canonicalize(Database.log));
            }
            if (restockCache) {
                for (final Map.Entry<Object,String> x : m.o2f.entrySet()) {
                    final String f = x.getValue();
                    f2b.put(f, new Bucket(
                        new CacheReference<String,Object>(f, x.getKey(), wiped),
                        true, null, false, null));
                }
            }

            // execute the transaction body
            try {
                if (!isQuery) {
                    final Receiver<?> flip = root.fetch(null, JODB.flip);
                    if (null != flip) { flip.run(null); }
                }
                r = Eventual.ref(body.run(root));
            } catch (final Exception e) {
                r = new Rejected<R>(e);
            }

            // persist the modifications
            while (!m.xxx.isEmpty()) {
                final Iterator<String> i = m.xxx.iterator();
                final String f = i.next();
                final Bucket b = f2b.get(f);
                i.remove();

                final Mac mac = allocMac(root);
                final ByteArrayOutputStream bytes = isQuery && !b.created
                    ? null : new ByteArrayOutputStream(256);
                final Object o = b.value.get();
                final Slicer out =
                    new Slicer(o, root, new MacOutputStream(mac, bytes));
                out.writeObject(o);
                out.flush();
                out.close();
                final ByteArray version = ByteArray.array(mac.doFinal());
                freeMac(mac);
                if (b.created || !version.equals(b.version)) {
                    if (null == bytes) {
                        throw new ProhibitedModification(Reflection.getName(
                            null != o ? o.getClass() : Void.class));
                    }
                    final OutputStream fout = m.update.write(f + ext);
                    bytes.writeTo(fout);
                    fout.flush();
                    fout.close();
                    f2b.put(f, new Bucket(b.value, false, version,
                                          out.isManaged(), out.getSplices()));
                }
            }
            m.update.commit();
        } catch (final Error e) {
            f2b.clear();
            
            // allow the caller to recover from an aborted transaction
            if (e instanceof OutOfMemoryError) { System.gc(); }
            final Throwable cause = e.getCause();
            if (cause instanceof Exception) { throw (Exception)cause; }
            throw new Exception(e);
        } finally {
            tx = null;
            m.update.close();
        }
        
        // output the log events for the committed transaction
        if (null != stderr) {
            while (!m.events.isEmpty()) {
                stderr.run(m.events.removeFirst());
            }
        }
        
        // schedule any services
        if (null != service) {
            while (!m.services.isEmpty()) {
                service.run(m.services.removeFirst());
            }
        }
        
        return r;
    }
    
    static private final String flip = ".flip";     // name of loop turn flipper

    /*
     * In testing, allocation of hash objects doubled serialization time, so I'm
     * keeping a pool of them. Sucky code is like cancer.
     */
    static private final String secret = ".secret"; // name of master MAC key
    private final ArrayList<Mac> macs = new ArrayList<Mac>();
    private SecretKeySpec master;                   // MAC key generation secret

    private Mac
    allocMac(final Root local) throws Exception {
        if (!macs.isEmpty()) { return macs.remove(macs.size() - 1); }
        if (null == master) {
            final ByteArray bits = local.fetch(null, secret);
            master = new SecretKeySpec(bits.toByteArray(), "HmacSHA256");
        }
        final Mac r = Mac.getInstance("HmacSHA256");
        r.init(master);
        return r;
    }

    private void
    freeMac(final Mac h) { macs.add(h); }
    
    /**
     * Determine the type of object stored in a stream.
     */
    static protected String
    identify(final InputStream s) throws IOException {
        final DataInputStream data = new DataInputStream(s);
        final String r;
        if (ObjectStreamConstants.STREAM_MAGIC != data.readShort()) {
            r = "! " + StreamCorruptedException.class.getName();
        } else {
            data.readShort();   // skip version number, assume compatible
            switch (data.read()) {
            case ObjectStreamConstants.TC_OBJECT: {
                switch (data.read()) {
                case ObjectStreamConstants.TC_CLASSDESC: { r = data.readUTF(); }
                break;
                case ObjectStreamConstants.TC_PROXYCLASSDESC: {
                    r = data.readInt() > 0 ? data.readUTF() : "proxy";
                }
                break;
                default: r = "! " + StreamCorruptedException.class.getName();
                }
            }
            break;
            case ObjectStreamConstants.TC_ARRAY: { r = "array"; }
            break;
            case ObjectStreamConstants.TC_STRING: { r = "string"; }
            break;
            case ObjectStreamConstants.TC_LONGSTRING: { r = "long string"; }
            break;
            case ObjectStreamConstants.TC_NULL: { r = "null"; }
            break;
            default: r = "! " + StreamCorruptedException.class.getName();
            }
        }
        try { data.close(); } catch (final Exception e) {}
        return r;
    }
}
