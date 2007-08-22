// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.test;

import static org.ref_send.Slot.var;
import static org.ref_send.promise.Fulfilled.ref;

import java.io.Serializable;

import org.ref_send.Slot;
import org.ref_send.promise.Promise;
import org.ref_send.promise.Volatile;
import org.ref_send.promise.eventual.Channel;
import org.ref_send.promise.eventual.Do;
import org.ref_send.promise.eventual.Eventual;

/**
 * Test condition operations.
 */
public final class
Logic {

    private
    Logic() {}

    /**
     * Creates a when block that compares against an expected value.
     * @param <T> referent type
     * @param expected  expected value
     */
    static public <T> Do<T,Promise<Boolean>>
    was(final T expected) {
        class Was extends Do<T,Promise<Boolean>> implements Serializable {
            static private final long serialVersionUID = 1L;

            public Promise<Boolean>
            fulfill(final T value) { return ref(expected.equals(value)); }
        }
        return new Was();
    }

    /**
     * Create a promise for the logical AND of multiple boolean promises.
     * @param _         eventual operator
     * @param condition each boolean promise
     * @return promise for the logical AND of each <code>condition</code>
     */
    static public Promise<Boolean>
    and(final Eventual _, final Volatile<Boolean>... condition) {
        if (0 == condition.length) { return ref(true); }
        final Channel<Boolean> answer = _.defer();
        final Slot<Integer> todo = var(condition.length);
        for (final Volatile<Boolean> test : condition) {
            class AND extends Do<Boolean,Void> implements Serializable {
                static private final long serialVersionUID = 1L;

                public Void
                fulfill(final Boolean value) {
                    if (value) {
                        todo.put(todo.get() - 1);
                        if (0 == todo.get()) {
                            answer.resolver.fulfill(true);
                        }
                    } else {
                        answer.resolver.fulfill(false);
                    }
                    return null;
                }
                public Void
                reject(final Exception e) { return answer.resolver.reject(e); }
            }
            _.when(test, new AND());
        }
        return answer.promise;
    }
}
