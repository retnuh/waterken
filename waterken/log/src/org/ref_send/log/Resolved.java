// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.log;

import org.ref_send.deserializer;
import org.ref_send.name;

/**
 * Logs resolution of a condition.
 * <p>
 * This kind of event is produced when a promise is
 * {@linkplain org.ref_send.promise.Resolver#resolve resolved}.
 * </p>
 * @see SentIf
 */
public class
Resolved extends Event {
    static private final long serialVersionUID = 1L;

    /**
     * globally unique identifier for the condition
     */
    public final String condition;
    
    /**
     * Constructs an instance.
     * @param anchor    {@link #anchor}
     * @param timestamp {@link #timestamp}
     * @param trace     {@link #trace}
     * @param condition {@link #condition}
     */
    public @deserializer
    Resolved(@name("anchor") final Anchor anchor,
             @name("timestamp") final Long timestamp,
             @name("trace") final Trace trace,
             @name("condition") final String condition) {
        super(anchor, timestamp, trace);
        this.condition = condition;
    }
}
