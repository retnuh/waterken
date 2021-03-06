// Copyright 2005-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.promise;

import java.io.Serializable;

import org.joe_e.Struct;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.name;

/**
 * A return from an {@linkplain Eventual#defer explicit promise creation}.
 * <p>
 * This class represents the reified {@linkplain Promise tail} and
 * {@linkplain Resolver head} of a reference:
 * {@link #promise -}{@code -}{@link #resolver &gt;}.
 * </p>
 * @param <T> referent type
 */
public class
Deferred<T> extends Struct implements Record, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * permission to access the referent
     */
    public final Promise<T> promise;

    /**
     * permission to resolve the referent
     */
    public final Resolver<T> resolver;

    /**
     * Constructs an instance.
     * @param promise   {@link #promise}
     * @param resolver  {@link #resolver}
     */
    public @deserializer
    Deferred(@name("promise") final Promise<T> promise,
             @name("resolver") final Resolver<T> resolver) {
        this.promise = promise;
        this.resolver = resolver;
    }
}
