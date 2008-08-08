// Copyright 2002-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.promise;

import java.io.Serializable;

import org.joe_e.Selfless;

/**
 * A promise that alleges to be fulfilled.
 * @param <T> referent type
 */
public abstract class
Fulfilled<T> implements Promise<T>, Selfless, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * Construct an instance.
     */
    protected
    Fulfilled() {}

    /**
     * Adapts an immediate reference to the {@link Promise} interface.
     * @param <T> referent type
     * @param value immediate referent
     * @return promise that {@linkplain #cast refers} to the <code>value</code>
     */
    static public <T> Promise<T>
    ref(final T value) {
        if (null==value) { return new Rejected<T>(new NullPointerException()); }
        if (value instanceof Double) {
            final Double d = (Double)value;
            if (d.isNaN()) {return new Rejected<T>(new ArithmeticException());}
            if (d.isInfinite()) {
                return new Rejected<T>(new ArithmeticException());
            }
        } else if (value instanceof Float) {
            final Float f = (Float)value;
            if (f.isNaN()) {return new Rejected<T>(new ArithmeticException());}
            if (f.isInfinite()) {
                return new Rejected<T>(new ArithmeticException());
            }
        }
        return new Inline<T>(value);
    }

    /**
     * Marks a point where deserialization of an object graph may be deferred.
     * <p>
     * If a referrer holds the promise returned by this method, instead of a
     * direct reference to the referent, the persistence engine may defer
     * deserialization of the referent until it is {@linkplain #cast accessed}.
     * </p>
     * @param <T> referent type
     * @param value {@linkplain #cast referent}
     * @return {@linkplain Fulfilled fulfilled} promise for <code>value</code>
     */
    static public <T> Fulfilled<T>
    detach(final T value) { return new Detachable<T>(value); }

    /**
     * Gets the corresponding reference for a fulfilled promise.
     * <p>
     * This method is the inverse of {@link #detach}.
     * </p>
     * <p>
     * This method will not throw an {@link Exception}.
     * </p>
     * @param <T> referent type
     * @param promise   {@linkplain Fulfilled fulfilled} promise
     * @return {@linkplain #cast corresponding} reference
     */
    static public <T> T
    near(final Volatile<T> promise) {
        try {
            return ((Fulfilled<T>)promise).cast();
        } catch (final Exception e) { throw new Error(e); }
    }

    // java.lang.Object interface

    /**
     * Is the given object the same?
     * @param x compared to object
     * @return <code>true</code> if the same, else <code>false</code>
     */
    public boolean
    equals(final Object x) {
        return x instanceof Fulfilled && same(near(this),near((Fulfilled<?>)x));
    }

    static private boolean
    same(final Object a, final Object b) {
        return null != a ? a.equals(b) : null == b;
    }

    /**
     * Calculates the hash code.
     */
    public final int
    hashCode() { return 0xF0F111ED; }
}
