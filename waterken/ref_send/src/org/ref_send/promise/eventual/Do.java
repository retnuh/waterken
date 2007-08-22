// Copyright 2002-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.promise.eventual;

import java.lang.reflect.TypeVariable;

import org.joe_e.Struct;
import org.ref_send.type.Typedef;

/**
 * A deferred code block.
 * @param <P> parameter type
 * @param <R> return type
 */
public abstract class
Do<P,R> extends Struct {

    /**
     * return type
     */
    static final TypeVariable R = Typedef.name(Do.class, "R");
    
    protected
    Do() {}

    /**
     * Notification of a fulfilled argument.
     * @param arg   argument to the code block
     * @return code block's return value
     * @throws Exception    any exception produced by the code block
     */
    public abstract R
    fulfill(P arg) throws Exception;

    /**
     * Notification of a rejected argument.
     * <p>
     * The default implementation throws <code>reason</code>.
     * </p>
     * @param reason    reason the code block's argument is not known
     * @return code block's return value
     * @throws Exception    any exception produced by the code block
     */
    public R
    reject(final Exception reason) throws Exception { throw reason; }
}
