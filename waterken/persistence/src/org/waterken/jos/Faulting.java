// Copyright 2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.jos;

import org.ref_send.promise.Fulfilled;
import org.waterken.vat.Root;

/**
 * An object faulting promise.
 */
final class
Faulting extends Fulfilled<Object>  {
    static private final long serialVersionUID = 1L;

    private final Root root;
    private final String name;

    Faulting(final Root root, final String name) {
        if (null == root) { throw new NullPointerException(); }
        if (null == name) { throw new NullPointerException(); }
        
        this.root = root;
        this.name = name;
    }

    // java.lang.Object interface

    public boolean
    equals(final Object x) {
        return x instanceof Faulting
            ? name.equals(((Faulting)x).name) &&
              root.equals(((Faulting)x).root)
            : super.equals(x);
    }

    // org.ref_send.promise.Volatile interface

    public Object
    cast() { return root.fetch(null, name); }
}
