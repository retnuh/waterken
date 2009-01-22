// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import static org.ref_send.promise.Fulfilled.detach;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.joe_e.Immutable;
import org.joe_e.Struct;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.reflect.Reflection;
import org.ref_send.list.List;
import org.ref_send.promise.eventual.Log;
import org.ref_send.promise.eventual.Receiver;
import org.ref_send.promise.eventual.Task;
import org.waterken.db.Creator;
import org.waterken.db.Database;
import org.waterken.db.Effect;
import org.waterken.db.Root;
import org.waterken.db.Transaction;
import org.waterken.http.Server;
import org.waterken.syntax.json.JSONDeserializer;

/**
 * The vat initialization transaction.
 */
public final class
VatInitializer extends Struct implements Transaction<PowerlessArray<String>> {

    private final Method make;
    private final String base;      // base URL for JSON serialization
    private final ByteArray body;   // JSON serialized arguments
    
    protected
    VatInitializer(final Method make, final String base, final ByteArray body) {
        this.make = make;
        this.base = base;
        this.body = body;
    }
    
    public PowerlessArray<String>
    run(final Root local) throws Exception {
        final String here = local.fetch(null, Database.here);
        final Receiver<Effect<Server>> effect=local.fetch(null,Database.effect);
        final Log log = local.fetch(null, Database.log);
        final Receiver<?> destruct = local.fetch(null, Database.destruct);
        
        local.link(HTTP.sessions, new SessionMaker(local));
        final Outbound outbound = new Outbound();
        local.link(VatInitializer.outbound, outbound);
        local.link(Database.wake, new Wake());
        final List<Task<?>> tasks = List.list();
        local.link(VatInitializer.tasks, tasks);
        final HTTP http = new HTTP(enqueue(effect,tasks), here, log,
                                   destruct, local, detach(outbound));
        local.link(VatInitializer.exports, http);
        final ConstArray<Type> signature =
            ConstArray.array(make.getGenericParameterTypes());
        ConstArray<Type> parameters = signature;
        if (parameters.length() != 0) {     // pop the eventual operator
            parameters = parameters.without(0);
        }
        final HTTP.Exports exports = http.crack();
        final ConstArray<?> optional = new JSONDeserializer().run(base,
            exports.connect(), parameters, exports.getCodebase(),
            body.asInputStream());
        final Object[] argv = new Object[signature.length()];
        if (argv.length != 0) { argv[0] = http; }
        for (int i = 0; i != optional.length(); ++i) {
            argv[i + 1] = optional.get(i);
        }
        final Object value = Reflection.invoke(make, null, argv);
        return PowerlessArray.array(exports.send(base).run(value));
    }
    
    static public String
    create(final Database<Server> parent, final String project,
           final String base, final String label,
           final Class<?> maker) throws Exception {
        final Method make = HTTP.dispatch(maker, "make");
        return parent.enter(Transaction.update,
                            new Transaction<PowerlessArray<String>>() {
            public PowerlessArray<String>
            run(final Root local) throws Exception {
                final Creator creator = local.fetch(null, Database.creator);
                return creator.run(project, base, label, new VatInitializer(
                    make, null, ByteArray.array((byte)'[', (byte)']'))).cast();
            }
        }).cast().get(0);
    }
    
    static private Receiver<Task<?>>
    enqueue(final Receiver<Effect<Server>> effect, final List<Task<?>> tasks) {
        class Enqueue extends Struct implements Receiver<Task<?>>, Serializable{
            static private final long serialVersionUID = 1L;

            public void
            run(final Task<?> task) {
                if (tasks.isEmpty()) {
                    effect.run(runTask());
                }
                tasks.append(task);
            }
        }
        return new Enqueue();
    }

    static private final class
    Wake extends Struct implements Transaction<Immutable>, Serializable {
        static private final long serialVersionUID = 1L;
        
        public Immutable
        run(final Root local) throws Exception {
            final List<Task<?>> tasks = local.fetch(null, VatInitializer.tasks);
            if (!tasks.isEmpty()) {
                final Receiver<Effect<Server>> effect =
                    local.fetch(null, Database.effect);
                effect.run(runTask());
            }
            final Outbound outbound= local.fetch(null, VatInitializer.outbound);
            for (final Pipeline x : outbound.getPending()) { x.resend(); }
            return null;
        }
    }
    
    static private Effect<Server>
    runTask() {
        return new Effect<Server>() {
            public void
            run(final Database<Server> vat) throws Exception {
                vat.enter(Transaction.update, new Transaction<Immutable>() {
                    public Immutable
                    run(final Root local) throws Exception {
                        final List<Task<?>> tasks =
                            local.fetch(null, VatInitializer.tasks);
                        final Task<?> task = tasks.pop();
                        if (!tasks.isEmpty()) {
                            final Receiver<Effect<Server>> effect =
                                local.fetch(null, Database.effect);
                            effect.run(runTask());
                        }
                        task.run();
                        return null;
                    }
                });
            }
        };
    }
    
    static protected final String outbound = ".outbound";
    static private   final String tasks = ".tasks";
    static protected final String exports = ".exports";
}
