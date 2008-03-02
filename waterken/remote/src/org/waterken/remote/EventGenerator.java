// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote;

import java.io.Serializable;

import org.joe_e.Equatable;
import org.joe_e.Struct;
import org.ref_send.log.Event;
import org.ref_send.log.Got;
import org.ref_send.log.Resolved;
import org.ref_send.log.SentIf;
import org.ref_send.log.Turn;
import org.ref_send.promise.eventual.Log;
import org.ref_send.promise.eventual.Loop;
import org.ref_send.var.Factory;
import org.ref_send.var.Receiver;
import org.waterken.uri.URI;
import org.waterken.vat.Effect;
import org.waterken.vat.Root;
import org.waterken.vat.Tracer;

/**
 * A log {@link Event} generator.
 */
public final class
EventGenerator {
    
    private
    EventGenerator() {}
    
    /**
     * Constructs an instance.
     * @param local     local address space
     * @param tracer    stack trace factory
     * @param erf       event receiver accessor
     */
    static public Log
    make(final Root local) {
        final Tracer tracer = (Tracer)local.fetch(null, Root.tracer);
        final Factory<Receiver<Event>> erf =
            (Factory)local.fetch(null, Root.events);
        final Loop<Effect> effect = (Loop)local.fetch(null,Root.effect); 
        class LogX extends Struct implements Log, Serializable {
            static private final long serialVersionUID = 1L;
            
            public void
            resolved(final Equatable condition) {
                final Receiver<Event> er = erf.run();
                if (null == er) { return; }
                
                final Turn turn = local.getTurn();
                log(er, new Resolved(local.getTurn(), tracer.get(),
                    URI.resolve(turn.loop, '#' + local.export(condition))));
            }

            public void
            got(final Equatable message) {
                final Receiver<Event> er = erf.run();
                if (null == er) { return; }
                
                final Turn turn = local.getTurn();
                log(er, new Got(local.getTurn(), tracer.get(),
                    URI.resolve(turn.loop, '#' + local.export(message))));
            }

            public void
            sentIf(final Equatable message, final Equatable condition) {
                final Receiver<Event> er = erf.run();
                if (null == er) { return; }
                
                final Turn turn = local.getTurn();
                log(er, new SentIf(local.getTurn(), tracer.get(),
                    URI.resolve(turn.loop, '#' + local.export(message)),
                    URI.resolve(turn.loop, '#' + local.export(condition))));
            }
            
            private void
            log(final Receiver<Event> er, final Event e) {
                effect.run(new Effect() { public void run() { er.run(e); } });
            }
        }
        return new LogX();
    }
}
