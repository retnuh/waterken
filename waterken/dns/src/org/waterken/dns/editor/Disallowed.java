// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.dns.editor;

import org.joe_e.Powerless;
import org.ref_send.deserializer;
import org.waterken.dns.Resource;

/**
 * A disallowed {@link Resource}.
 */
public class
Disallowed extends RuntimeException implements Powerless {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     */
    public @deserializer
    Disallowed() {}
}
