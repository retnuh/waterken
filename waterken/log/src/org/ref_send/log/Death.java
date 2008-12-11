// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.log;

import org.ref_send.deserializer;
import org.ref_send.name;

/**
 * Logs termination of an event loop.
 */
public class
Death extends Event {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     * @param anchor    {@link #anchor}
     * @param trace     {@link #trace}
     */
    public @deserializer
    Death(@name("anchor") final Anchor anchor,
          @name("trace") final Trace trace) {
        super(anchor, trace);
    }
}
