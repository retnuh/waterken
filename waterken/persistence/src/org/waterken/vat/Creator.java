// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.vat;

import org.web_send.graph.Collision;

/**
 * A {@link Vat} factory.
 */
public interface
Creator {
    
    /**
     * Loads a project's class library.
     * @param project   project name
     * @return corresponding class library
     */
    ClassLoader
    load(String project) throws Exception;

    /**
     * Creates a new {@link Vat}.
     * @param initialize    first transaction to run on the new vat
     * @param project       corresponding project name
     * @param name          vat name, or <code>null</code> for generated name
     * @throws Collision    <code>name</code> has already been used
     */
    <R> R
    create(Transaction<R> initialize,
           String project, String name) throws Exception;
}
