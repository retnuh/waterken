// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.vat;

/**
 * A {@link Vat#enter transaction} body.
 */
public interface
Transaction<R> {

    /**
     * Executes the transaction.
     * @param local {@link Vat} root
     * @return any return
     * @throws Exception    any problem
     */
    R
    run(Root local) throws Exception;
}
