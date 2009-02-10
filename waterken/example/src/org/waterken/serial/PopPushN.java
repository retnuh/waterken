// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.serial;

import static org.ref_send.promise.Fulfilled.ref;
import static org.ref_send.test.Logic.and;
import static org.ref_send.test.Logic.was;

import org.joe_e.array.ConstArray;
import org.ref_send.list.List;
import org.ref_send.promise.Promise;
import org.ref_send.promise.Volatile;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.promise.eventual.Task;

/**
 * Eventual invocation tests.
 * <p>
 * This class provides an introduction to eventual operations by using them to
 * test the elements of a series that isn't produced until later. Within these
 * lines, time and space are not just curved, they're bent. You should also
 * probably save this example until later.
 * </p>
 */
public final class
PopPushN {
    private PopPushN() {}
    
    /**
     * Runs a unit test.
     * @param _ eventual operator
     * @param n number of test iterations
     */
    static public Promise<Boolean>
    make(final Eventual _, final int n) {
        final Series<Integer> x = Serial.make(_);
        
        /*
         * Check that the first n integers in the series will be the
         * numbers from 0 through n.
         */
        ConstArray<Volatile<Boolean>> r = new ConstArray<Volatile<Boolean>>();
        for (int i = 0; i != n; ++i) {
            r = r.with(_.when(x.consume(), was(i)));
        }
        
        /*
         * Append the numbers 0 through n to the series.
         */
        for (int i = 0; i != n; ++i) {
            x.produce(ref(i));
        }
        
        return and(_, r);
    }
    
    // Command line interface

    /**
     * Executes the test.
     * @param args  argument string
     * @throws Exception    test failed
     */
    static public void
    main(final String[] args) throws Exception {
        final int n = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        
        final List<Task<?>> work = List.list();
        final Promise<Boolean> result = make(new Eventual(work.appender()), n);
        while (!work.isEmpty()) { work.pop().run(); }
        if (!result.cast()) { throw new Exception("test failed"); }
    }
}
