// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.bounce;

import static org.ref_send.promise.eventual.Eventual.ref;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.joe_e.Struct;
import org.joe_e.array.BooleanArray;
import org.joe_e.array.ByteArray;
import org.joe_e.array.CharArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.DoubleArray;
import org.joe_e.array.FloatArray;
import org.joe_e.array.ImmutableArray;
import org.joe_e.array.IntArray;
import org.joe_e.array.LongArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.array.ShortArray;
import org.ref_send.promise.Promise;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.eventual.Channel;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.promise.eventual.Receiver;
import org.ref_send.promise.eventual.Sink;

/**
 * A {@link Wall} implementation.
 */
public final class
Bounce {
    private Bounce() {}

    /**
     * Constructs an instance.
     * @param _ eventual operator
     */
    static public Wall
    make(final Eventual _) {
        final Receiver<?> normal = new Sink<Void>();
        final Rejected<Receiver<?>> rejected =
            new Rejected<Receiver<?>>(new Exception());
        final Channel<Boolean> d = _.defer(); 
        class WallX extends Struct implements Wall, Serializable {
            static private final long serialVersionUID = 1L;

            public Promise<AllTypes>
            getAll() {
                return ref(new AllTypes(
                    BooleanArray.array(true, false),
                    CharArray.array('a', '\"', '\\', '<', '>', '/', '\b',
                                    '\f', '\n', '\r', '\t', '\u0085'),
                    FloatArray.array(0.0F,
                          Float.MAX_VALUE, Float.MIN_VALUE,
                          -Float.MAX_VALUE, -Float.MIN_VALUE,
                          // Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                          Float.NaN),
                    DoubleArray.array(0.0,
                          Double.MAX_VALUE, Double.MIN_VALUE,
                          -Double.MAX_VALUE, -Double.MIN_VALUE,
                          // Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                          Double.NaN),
                    ByteArray.array((byte)0, Byte.MAX_VALUE, Byte.MIN_VALUE),
                    ShortArray.array((short)0, Short.MAX_VALUE,Short.MIN_VALUE),
                    IntArray.array(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                    LongArray.array(0L, (1L << 53) - 1, -((1L << 53) - 1)),
                    "a \" \\ / </ < > \b \f \n \r \t \u0085",
                    new ConstArray<Receiver<?>>().
                        with(normal).
                        with(null).
                        with(rejected._(Receiver.class)),
                    new ConstArray<Promise<?>>().
                        with(ref(false)).
                        with(ref(Integer.MAX_VALUE)).
                        with(ref("a")).
                        with(d.promise),
                    ConstArray.array(
                        new ImmutableArray<PowerlessArray<Boolean>>().
                            with(PowerlessArray.array(true)),
                        new BigInteger("9223372036854775808"),
                        new BigDecimal("3.14")
                    )));
            }

            public <A> Promise<A>
            bounce(final A a) { return ref(a); }
        }
        return new WallX();
    }
}
