// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.vat;

import org.joe_e.Immutable;
import org.joe_e.file.InvalidFilenameException;
import org.ref_send.promise.Promise;

/**
 * A {@link Vat} factory.
 */
public interface
Creator {

    /**
     * Creates a new {@link Vat}.
     * @param project   corresponding project name
     * @param name      vat name, or <code>null</code> for generated name
     * @param setup     initializes the new vat
     * @return return from <code>setup</code>
     * @throws InvalidFilenameException <code>name</code> not available
     * @throws ProhibitedModification   in an {@link Vat#extend} transaction
     */
    <R extends Immutable> Promise<R>
    run(String project, String name,
        Transaction<R> setup) throws InvalidFilenameException,
                                     ProhibitedModification;
}
