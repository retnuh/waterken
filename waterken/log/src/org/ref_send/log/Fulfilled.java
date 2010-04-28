// Copyright 2009 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.log;

import org.ref_send.deserializer;
import org.ref_send.name;

/**
 * Logs {@linkplain org.ref_send.promise.Resolver#apply fulfillment} of a
 * promise.
 */
public class
Fulfilled extends Resolved {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     * @param anchor    {@link #anchor}
     * @param timestamp {@link #timestamp}
     * @param trace     {@link #trace}
     * @param condition {@link #condition}
     */
    public @deserializer
    Fulfilled(@name("anchor") final Anchor anchor,
              @name("timestamp") final Long timestamp,
              @name("trace") final Trace trace,
              @name("condition") final String condition) {
        super(anchor, timestamp, trace, condition);
    }
}
