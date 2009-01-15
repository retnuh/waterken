// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.bang;

import static org.ref_send.test.Logic.and;
import static org.ref_send.test.Logic.was;

import java.io.Serializable;

import org.joe_e.Struct;
import org.joe_e.array.ConstArray;
import org.ref_send.list.List;
import org.ref_send.promise.Promise;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.promise.eventual.Task;
import org.ref_send.test.Test;

/**
 * An introduction to eventual operations in Java.
 * <p>
 * This class provides an introduction to eventual operations by using them to
 * update and query a counter held in an object of type {@link Drum}.
 * </p>
 */
public final class
Main extends Struct implements Test, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * eventual operator
     * <p>
     * By convention, an instance of {@link Eventual} is held in a variable
     * named "_" and referred to as the "eventual operator". The eventual
     * operator provides all eventual control flow operations, as well as the
     * ability to produce "eventual references", which are references that
     * schedule future invocation of a method, instead of invoking a method
     * immediately. All references known to be eventual are also stored in
     * variables whose name is suffixed with the '_' character. Consequently,
     * you can scan down a page of code, looking for the character sequence "_."
     * to find all the operations that are expected to be eventual.
     * </p>
     */
    private final Eventual _;

    /**
     * Constructs an instance
     * @param _ eventual operator
     */
    public
    Main(final Eventual _) {
        this._ = _;
    }
    
    /**
     * Constructs an instance.
     * <p>
     * This method is called by the infrastructure code that manages the
     * lifecycle of vats. The return from this method is an object that will be
     * returned to the creator of the new vat. In this case, the vat creator
     * will get an eventual reference of type {@link Test} to an instance of
     * this class. In all cases, the return type for a make() method must be
     * either an interface, or a {@link Promise}.
     * </p>
     * 
     * @param framework vat permissions
     */
    static public Test
    make(final Eventual _) {
        /*
         * In this case, we only need the eventual operator, so the rest of the
         * provided permissions are ignored.
         */ 
        return new Main(_);
    }
    
    // Command line interface

    /**
     * Executes the test.
     * <p>
     * This class can also be run from the command line, to run tests against a
     * local, transient {@link Drum}. Most factory classes won't provide a
     * command line test suite and so won't have a {@link #main} method.
     * </p>
     * @param args  ignored
     * @throws Exception    test failed
     */
    static public void
    main(final String[] args) throws Exception {
        /*
         * All the eventual control flow operations bottom out in runnable tasks
         * on an event loop. This method provides a simple implementation for
         * the event loop. The Waterken Server provides a more complete
         * implementation that supports multiple concurrent event loops with
         * transparent persistence and across the network messaging.
         */
        final List<Task<?>> work = List.list();
        final Eventual _ = new Eventual(work.appender());
        final Test test = new Main(_);
        final Promise<Boolean> result = test.start();
        while (!work.isEmpty()) { work.pop().run(); }
        if (!result.cast()) { throw new Exception("test failed"); }
    }
    
    // org.ref_send.test.Test interface

    /**
     * Starts a {@linkplain #test test}.
     */
    public Promise<Boolean>
    start() { return test(subject(), 0); }
    
    // org.waterken.bang.Main interface
    
    /**
     * Creates a new test subject.
     */
    public Drum
    subject() { return Bang.make(); }
    
    /**
     * Tests a {@link Drum}.
     * @param drum  test subject
     * @param n     initial {@linkplain Drum#getHits hit count}
     */
    public Promise<Boolean>
    test(final Drum drum, final int n) {
        /*
         * First, ensure that we have an eventual reference, since the caller
         * may have provided an immediate reference. The _() operation takes
         * a reference that may be either immediate or eventual and returns a
         * reference that is guaranteed to be eventual.
         */ 
        final Drum drum_ = _._(drum);
        
        /*
         * All tests are now done using the created eventual reference. The
         * provided reference is no longer used, since it can't be trusted to be
         * an eventual reference.
         */
        
        /*
		 * Start the test sequence by checking that the caller provided the
		 * correct value for the current hit count on the provided drum. We get
		 * the initial hit count by doing an eventual invocation of the
		 * getHits() method. If the provided drum is in another vat, this
		 * invocation will result in an HTTP GET request being sent to the
		 * hosting server. The return from the getHits() invocation is a promise
		 * for the number of hits. Using the when() operation, we register an
		 * observer on this promise, to receive a notification after the HTTP
		 * GET response has come back. The observer, constructed by the was()
		 * method, produces a promise for a boolean, indicating whether or not
		 * the number of hits specified in the HTTP GET response was the number
		 * expected. We'll hold onto this promise and use it to produce the
		 * promise returned to our caller.
		 */
        final Promise<Boolean> zero = _.when(drum_.getHits(), was(n));
        
        /*
		 * Increment the hit counter by doing an eventual invocation of the
		 * bang() method. If the provided drum is in another vat, this
		 * invocation will result in an HTTP POST request being sent to the
		 * hosting server.
		 */
        drum_.bang(1);
        
        /*
         * Requests sent on an eventual reference are sent in order, and so
         * another check of the drum's hit count will see a value 1 more than
         * the previous check.
         */
        final Promise<Boolean> one = _.when(drum_.getHits(), was(n + 1));
        
        // We can queue up as many requests as we like...
        drum_.bang(2);
        
        // ...and they will all be sent in order.
        final Promise<Boolean> three = _.when(drum_.getHits(), was(n + 3));
        
        /*
         * The Waterken server can log the causal chaining of all these events.
         * Comments can be inserted into this log via the eventual operator.
         */
        _.log.comment("all bang requests queued");
        
        /*
         * We now have 3 promises for checks on the expected value of the drum's
         * hit count. We'll combine these 3 promises into 1 by doing an eventual
         * AND operation on them. The promise returned to our caller will
         * resolve as soon as any one of these 3 promises doesn't resolve to
         * true, or after all of them have resolved to true. Note that none of
         * the HTTP requests have actually been sent yet; we've just scheduled
         * them to be sent and setup code to be run when the responses
         * eventually come back.
         */
        return and(_, ConstArray.array(zero, one, three));
        
        /*
         * In total, we've scheduled 3 GET requests and 2 POST requests and
         * produced a promise which will only resolve to true after all of these
         * network requests have completed successfully. If any of these
         * communications are interrupted, due to a server crash or lost
         * connection, the software will remember where it left off and resume
         * as soon as network connections can be re-established, which the
         * software will also periodically retry. Regardless of how often this
         * process is interrupted, or for how long, the hit count on the drum
         * will only be incremented by 3, the number specified by the algorithm
         * above.
         */
    }
}
