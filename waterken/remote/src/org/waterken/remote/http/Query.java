// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

/**
 * An idempotent {@link Message} whose return may be affected by a subsequent
 * {@link Update}.
 */
abstract class
Query extends Message {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     * @param id    {@link #id}
     */
    Query(final int id) {
        super(id);
    }
}
