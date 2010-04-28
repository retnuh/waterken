// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.log;

import java.io.Serializable;

import org.joe_e.Powerless;
import org.joe_e.Struct;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.name;

/**
 * Logs an event.
 */
public class
Event extends Struct implements Powerless, Record, Serializable {
    static private final long serialVersionUID = 1L;
    
    /**
     * event identifier
     */
    public final Anchor anchor;

    /**
     * difference, measured in milliseconds, between the time the event occurred
     * and midnight, January 1, 1970 UTC (optional)
     */
    public final Long timestamp;
    
    /**
     * trace of the call site that produced the event (optional)
     */
    public final Trace trace;
    
    /**
     * Constructs an instance.
     * @param anchor    {@link #anchor}
     * @param timestamp {@link #timestamp}
     * @param trace     {@link #trace}
     */
    public @deserializer
    Event(@name("anchor") final Anchor anchor,
          @name("timestamp") final Long timestamp,
          @name("trace") final Trace trace) {
        this.anchor = anchor;
        this.timestamp = timestamp;
        this.trace = trace;
    }
}
