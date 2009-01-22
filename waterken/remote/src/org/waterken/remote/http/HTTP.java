// Copyright 2006-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import static java.lang.reflect.Modifier.isStatic;
import static org.ref_send.promise.Fulfilled.ref;

import java.io.Serializable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.Token;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.charset.URLEncoding;
import org.joe_e.reflect.Reflection;
import org.ref_send.promise.Fulfilled;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.eventual.Compose;
import org.ref_send.promise.eventual.Do;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.promise.eventual.Log;
import org.ref_send.promise.eventual.Receiver;
import org.ref_send.promise.eventual.Task;
import org.ref_send.type.Typedef;
import org.waterken.db.Creator;
import org.waterken.db.Database;
import org.waterken.db.Effect;
import org.waterken.db.Root;
import org.waterken.http.Server;
import org.waterken.remote.Messenger;
import org.waterken.remote.Remote;
import org.waterken.syntax.BadSyntax;
import org.waterken.syntax.Exporter;
import org.waterken.syntax.Importer;
import org.waterken.syntax.json.JSONSerializer;
import org.waterken.uri.Header;
import org.waterken.uri.Query;
import org.waterken.uri.URI;

/**
 * A web-key interface to a {@link Root}.
 */
/* package */ final class
HTTP extends Eventual implements Serializable {
    static private final long serialVersionUID = 1L;    
    
    private   final Root local;                         // vat root
    private   final Fulfilled<Outbound> outbound;       // active msg pipelines
    
    private   final Creator creator;                    // sub-vat factory
    private   final Receiver<Effect<Server>> effect;    // tx effect scheduler

    private
    HTTP(final Receiver<Task<?>> enqueue,
         final String here, final Log log, final Receiver<?> destruct,
         final Root local, final Fulfilled<Outbound> outbound) {
        super(new Token(), enqueue, here, log, destruct);
        this.local = local;
        this.outbound = outbound;
        
        creator = local.fetch(null, Database.creator);
        effect = local.fetch(null, Database.effect);
    }
    
    // org.ref_send.promise.eventual.Eventual interface

    public @Override @SuppressWarnings("unchecked") <R> R
    spawn(final String label, final Class<?> maker, final Object... argv) {
        Method make = null;
        try {
            for (final Method m : Reflection.methods(maker)) {
                if ("make".equals(m.getName()) &&
                        Modifier.isStatic(m.getModifiers())) {
                    make = m;
                    break;
                }
            }
        } catch (final Exception e) { throw new Error(e); }
        final Class<?> R = make.getReturnType();
        try {
            final Exports http = new Exports(this);
            final ByteArray body =
                new JSONSerializer().run(http.export(), ConstArray.array(argv));  
            final String href = creator.run(null, here, label,
                new VatInitializer(make, here, body)).cast().get(0);
            return (R)http.connect().run(href,here,make.getGenericReturnType());
        } catch (final BadSyntax e) {
            return new Rejected<R>((Exception)e.getCause())._(R);
        } catch (final Exception e) {
            return new Rejected<R>(e)._(R);
        }
    }
    
    // org.waterken.remote.http.Exports interface
    
    static protected HTTP.Exports
    make(final Receiver<Task<?>> enqueue,
         final Root local, final Fulfilled<Outbound> outbound) {
        final String here = local.fetch(null, Database.here);
        final Log log = local.fetch(null, Database.log);
        final Receiver<?> destruct = local.fetch(null, Database.destruct);
        return new Exports(new HTTP(enqueue,here,log,destruct, local,outbound));
    }
    
    static protected final class
    Exports extends Struct implements Messenger, Serializable {
        static private final long serialVersionUID = 1L;
        
        protected final HTTP _;
        
        private
        Exports(final HTTP _) {
            this._ = _;
        }
        
        // org.waterken.remote.Messenger interface

        public void
        when(final String href, final Remote proxy,
             final Do<Object,?> observer) {
            if (isPromise(URI.fragment("", href))) {
                peer(href).when(href, proxy, observer);
            } else {
                final Class<?> p = Typedef.raw(Compose.parameter(observer));
                _.when(ref(_.cast(p, proxy)), observer);
            }
        }
        
        public Object
        invoke(final String href, final Object proxy,
               final Method method, final Object... arg) {
            return peer(href).invoke(href, proxy, method, arg);
        }
        
        // org.waterken.remote.http.Exports.HTTP interface
        
        private Caller
        peer(final String href) {
            final String peer = URI.resolve(href, ".");
            final String peerKey = ".peer-" + URLEncoding.encode(peer);
            Pipeline msgs = _.local.fetch(null, peerKey);
            if (null == msgs) {
                final String name = _.local.export(new Token(), false);
                msgs = new Pipeline(name, URI.resolve(_.here, peer),
                                    _.effect, _.outbound);
                _.local.link(peerKey, msgs);
            }
            return new Caller(this, msgs);
        }
        
        /**
         * Gets the base URL for this URL space.
         */
        protected String
        getHere() { return _.here; }
        
        protected ClassLoader
        getCodebase() { return _.local.fetch(null, Database.code); }
        
        /**
         * Constructs a reference importer.
         */
        protected Importer
        connect() {
            final Importer next=Remote.connect(_, _.deferred, this, _.here);
            class ImporterX extends Struct implements Importer, Serializable {
                static private final long serialVersionUID = 1L;

                public Object
                run(final String href, final String base,
                                       final Type type) throws Exception {
                    final String URL=null!=base ? URI.resolve(base,href) : href;
                    return Header.equivalent(URI.resolve(URL, "."), _.here)
                        ? reference(URI.fragment("", URL))
                    : next.run(URL, null, type);
                }
            }
            return new ImporterX();
        }

        /**
         * Constructs a reference exporter.
         */
        protected Exporter
        export() {
            class ExporterX extends Struct implements Exporter, Serializable {
                static private final long serialVersionUID = 1L;

                public String
                run(final Object object) {
                    return href(_.local.export(object, false), isPBC(object) ||
                        !(Eventual.promised(object) instanceof Fulfilled));
                }
            }
            return Remote.export(_.deferred, new ExporterX());
        }
        
        protected Exporter
        send(final String base) {
            final Exporter export = export();
            class ExporterX extends Struct implements Exporter, Serializable {
                static private final long serialVersionUID = 1L;

                public String
                run(final Object x) {
                    final String absolute = URI.resolve(_.here, export.run(x));
                    return null != base ? URI.relate(base, absolute) : absolute;
                }
            }
            return new ExporterX();
        }

        /**
         * Calls {@link Root#getTransactionTag()}.
         */
        protected String
        getTransactionTag() {
            final Task<String> tagger = _.local.fetch(null, Database.tagger);
            try { return tagger.run(); } catch (final Exception e) {return "";}
        }
        
        /**
         * Receives an operation.
         * @param query     request query string
         * @param member    corresponding operation
         * @param op        operation to run
         * @return <code>op</code> return value
         */
        protected Object
        execute(final String query, final Member member, final Task<Object> op){
            final String x = session(query);
            if (null == x) {
                try {
                    return op.run();
                } catch (final Exception e) { return new Rejected<Object>(e); }
            }
            final ServerSideSession session = _.local.fetch(null, x);
            return session.once(window(query), message(query), member, op);
        }
        
        /**
         * Fetches a message target.
         * @param query web-key argument string
         * @return target reference
         */
        protected Object
        reference(final String query) {
            final String s = subject(query);
            return null==s || s.startsWith(".") ? null : _.local.fetch(null, s);
        }
    }
    
    /*
     * web-key parameters
     * x:   message session secret
     * w:   message window number
     * m:   intra-window message number
     * s:   message target key
     * q:   message operation identifier, typically the method name
     * o:   present if web-key is a promise
     */
    
    /**
     * Constructs a live web-key for a GET request.
     * @param href      web-key
     * @param predicate predicate string, or <code>null</code> if none
     */
    static protected String
    get(final String href, final String predicate) {
        String query = URI.fragment("", href);
        if ("".equals(query)) {
            query = "s=";
        }
        if (null != predicate) {
            query += "&q=" + URLEncoding.encode(predicate);
        }
        return URI.resolve(href, "./?" + query);
    }
    
    /**
     * Constructs a live web-key for a POST request.
     * @param href          web-key
     * @param predicate     predicate string, or <code>null</code> if none
     * @param sessionKey    message session key
     * @param window        message window number
     * @param message       intra-window message number
     */
    static protected String
    post(final String href, final String predicate,
         final String sessionKey, final long window, final int message) {
        String query = URI.fragment("", href);
        if ("".equals(query)) {
            query = "s=";
        }
        if (null != predicate) {
            query += "&q=" + URLEncoding.encode(predicate);
        }
        if (null == sessionKey) { throw new NullPointerException(); }
        query += "&x=" + URLEncoding.encode(sessionKey);
        query += "&w=" + window;
        query += "&m=" + message;
        return URI.resolve(href, "./?" + query);
    }

    /**
     * key bound to the session maker in all vats
     */
    static protected final String sessions = "sessions";
    
    /**
     * Constructs a live web-key for session initialization.
     * @param peer  peer vat URL
     */
    static protected String
    init(final String peer) {
        return URI.resolve(peer, "./?s=" + URLEncoding.encode(sessions) +
                                   "&q=" + URLEncoding.encode("create"));          
    }
    
    /**
     * Constructs a web-key.
     * @param subject   target object key
     * @param isPromise Is the target object a promise?
     */
    static protected String
    href(final String subject, final boolean isPromise) {
        return "#"+ (isPromise ? "o=&" : "") + "s="+URLEncoding.encode(subject);
    }

    /**
     * Extracts the subject key from a web-key.
     * @param q web-key argument string
     * @return corresponding subject key
     */
    static private String
    subject(final String q) { return Query.arg(null, q, "s"); }
    
    /**
     * Extracts the predicate string from a web-key.
     * @param q web-key argument string
     * @return corresponding predicate string
     */
    static protected String
    predicate(final String q) { return Query.arg(null, q, "q"); }
    
    /**
     * Is the given web-key a promise web-key?
     * @param q web-key argument string
     * @return <code>true</code> if a promise, else <code>false</code>
     */
    static protected boolean
    isPromise(final String q) { return null != Query.arg(null, q, "o"); }
    
    /**
     * Extracts the session key.
     * @param q web-key argument string
     * @return corresponding session key
     */
    static private String
    session(final String q) { return Query.arg(null, q, "x"); }
    
    static private long
    window(final String q) { return Long.parseLong(Query.arg(null, q, "w")); }
    
    static private int
    message(final String q) { return Integer.parseInt(Query.arg("0", q, "m")); }
    
    /**
     * Is the given object pass-by-construction?
     * @param object  candidate object
     * @return <code>true</code> if pass-by-construction,
     *         else <code>false</code>
     */
    static protected boolean
    isPBC(final Object object) {
        final Class<?> type = null != object ? object.getClass() : Void.class;
        return String.class == type ||
            Integer.class == type ||
            Boolean.class == type ||
            Long.class == type ||
            Byte.class == type ||
            Short.class == type ||
            Character.class == type ||
            Double.class == type ||
            Float.class == type ||
            Void.class == type ||
            java.math.BigInteger.class == type ||
            java.math.BigDecimal.class == type ||
            org.ref_send.Record.class.isAssignableFrom(type) ||
            Throwable.class.isAssignableFrom(type) ||
            org.joe_e.array.ConstArray.class.isAssignableFrom(type) ||
            org.ref_send.promise.Volatile.class.isAssignableFrom(type);
    }
    
    /**
     * Gets the corresponding property name.
     * <p>
     * This method implements the standard Java beans naming conventions.
     * </p>
     * @param method    candidate method
     * @return name, or null if the method is not a property accessor
     */
    static protected String
    property(final Method method) {
        final String name = method.getName();
        String r =
            name.startsWith("get") &&
            (name.length() == "get".length() ||
             Character.isUpperCase(name.charAt("get".length()))) &&
            method.getParameterTypes().length == 0
                ? name.substring("get".length())
            : (name.startsWith("is") &&
               (name.length() != "is".length() ||
                Character.isUpperCase(name.charAt("is".length()))) &&
               method.getParameterTypes().length == 0
                ? name.substring("is".length())
            : null);
        if (null != r && 0 != r.length() &&
                (1 == r.length() || !Character.isUpperCase(r.charAt(1)))) {
            r = Character.toLowerCase(r.charAt(0)) + r.substring(1);
        }
        return r;
    }
    
    /**
     * synthetic modifier
     */
    static private final int synthetic = 0x1000;
    
    /**
     * Is the synthetic flag set?
     * @param flags Java modifiers
     * @return <code>true</code> if synthetic, else <code>false</code>
     */
    static private boolean
    isSynthetic(final int flags) { return 0 != (flags & synthetic); }

    /**
     * Finds a named method.
     * @param target    invocation target
     * @param name      method name
     * @return corresponding method, or <code>null</code> if not found
     */
    static public Method
    dispatch(final Object target, final String name) {
        final Class<?> type = null != target ? target.getClass() : Void.class;
        final boolean c = Class.class == type;
        Method r = null;
        for (final Method m : Reflection.methods(c ? (Class<?>)target : type)) {
            final int flags = m.getModifiers();
            if (c == isStatic(flags) && !isSynthetic(flags)) {
                String mn = property(m);
                if (null == mn) {
                    mn = m.getName();
                }
                if (name.equals(mn)) {
                    if (null != r) { return null; }
                    r = m;
                }
            }
        }
        return r;
    }
}
