// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.web_send.split;

import java.io.Serializable;

import org.joe_e.Struct;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.ref_send.promise.eventual.Receiver;

/**
 * A base class for a return from a {@link Splitter} interface.
 */
public class
Component extends Struct implements Record, Serializable {
    static private final long serialVersionUID = 1L;
    
    /**
     * permission to destruct the corresponding vat
     */
    public final Receiver<?> destruct;
    
    /**
     * Constructs an instance.
     * @param destruct  {@link #destruct}
     */
    public @deserializer
    Component(@name("destruct") final Receiver<?> destruct) {
        this.destruct = destruct;
    }
}
