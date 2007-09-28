// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.model;

/**
 * A {@link Model} factory.
 */
public interface
Creator {

    /**
     * Creates a new {@link Model}.
     * @param label         model label
     * @param initialize    first transaction to run on the new model
     * @param project       corresponding project name
     * @throws NoLabelReuse <code>label</code> has already been used
     */
    <R> R
    run(String label,
        Transaction<R> initialize, String project) throws NoLabelReuse;
}
