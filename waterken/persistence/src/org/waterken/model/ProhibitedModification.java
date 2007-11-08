// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.model;

import org.joe_e.Powerless;

/**
 * Signals an attempt to modify persistent state in an {@link Model#extend}
 * {@link Model#enter transaction}.
 */
public class
ProhibitedModification extends Error implements Powerless {
    static private final long serialVersionUID = 1L;
    
    /**
     * modified object type
     */
    public final Class type;

    /**
     * Constructs an instance.
     * @param type  {@link #type}
     */
    public
    ProhibitedModification(final Class type) {
        this.type = type;
    }
}
