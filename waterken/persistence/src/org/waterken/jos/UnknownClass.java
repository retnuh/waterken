// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.jos;

import org.joe_e.Powerless;
import org.ref_send.Record;
import org.ref_send.deserializer;

/**
 * Signals a {@link ClassNotFoundException} during deserialization.
 */
public class
UnknownClass extends RuntimeException implements Powerless, Record {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     */
    public @deserializer
    UnknownClass() {}
    
    UnknownClass(final String message) {
        super(message);
    }
}
