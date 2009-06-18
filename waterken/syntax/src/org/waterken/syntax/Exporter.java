// Copyright 2002-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax;

import org.joe_e.inert;

/**
 * A reference exporter.
 */
public interface
Exporter {

    /**
     * Exports a reference.
     * @param target    reference to export
     * @return exported URL
     */
    String apply(@inert Object target);
}
