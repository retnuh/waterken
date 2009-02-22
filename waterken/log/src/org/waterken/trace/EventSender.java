// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.trace;

import java.lang.reflect.Method;

import org.joe_e.reflect.Reflection;
import org.ref_send.log.Comment;
import org.ref_send.log.Event;
import org.ref_send.log.Got;
import org.ref_send.log.Problem;
import org.ref_send.log.Resolved;
import org.ref_send.log.Returned;
import org.ref_send.log.Sent;
import org.ref_send.log.SentIf;
import org.ref_send.promise.Log;
import org.ref_send.promise.Receiver;

/**
 * Event logging infrastructure.
 */
public final class
EventSender {
    private EventSender() {}
    
    /**
     * Constructs a log event generator.
     * @param stderr    log event output factory
     * @param mark      event counter
     * @param tracer    stack tracer
     */
    static public Log
    make(final Receiver<Event> stderr, final Marker mark, final Tracer tracer) {
        class LogX extends Log {
            static private final long serialVersionUID = 1L;

            public @Override void
            comment(final String text) {
                stderr.run(new Comment(mark.run(), tracer.traceHere(), text));
            }
            
            public @Override void
            problem(final Exception reason) {
                stderr.run(new Problem(mark.run(),tracer.traceException(reason),
                                       tracer.readException(reason), reason));
            }

            public @Override void
            got(final String message, final Class<?> concrete, Method method) {
                if (null != concrete) {
                    try {
                        method = Reflection.method(concrete, method.getName(),
                                                   method.getParameterTypes());
                    } catch (final NoSuchMethodException e) {}
                }
                stderr.run(new Got(mark.run(),
                    null!=method ? tracer.traceMember(method) : null, message));
            }

            public @Override void
            returned(final String message) {
                stderr.run(new Returned(mark.run(), null, message));
            }

            public @Override void
            sent(final String message) {
                stderr.run(new Sent(mark.run(), tracer.traceHere(), message));
            }

            public @Override void
            resolved(final String condition) {
                stderr.run(new Resolved(mark.run(), tracer.traceHere(),
                                        condition));
            }

            public @Override void
            sentIf(final String message, final String condition) {
                stderr.run(new SentIf(mark.run(), tracer.traceHere(),
                                      message, condition));
            }
        }
        return new LogX();
    }
}
