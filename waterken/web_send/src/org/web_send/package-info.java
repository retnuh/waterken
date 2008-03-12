// Copyright 2007-2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html

/**
 * Inter-model messaging.
 * <p>When using the Waterken Server, each reference created by an application
 * can be exported to HTTP clients as a URL. These clients, including other
 * Waterken Server instances, can then interact with the referenced object by
 * sending HTTP requests using the exported URL.  The API in this package
 * provides additional access to the HTTP messaging layer.</p>
 * <h3><a name="status" href="#status">Accessing HTTP error status codes</a></h3>
 * <p>Using an
 * {@linkplain org.ref_send.promise.eventual.Eventual#cast eventual reference},
 * code written to the ref_send API can send HTTP requests. For example, the
 * following code:</p>
 * <pre>
 * final Drum drum_ = &hellip;
 * final Promise&lt;Integer&gt; hits = drum_.getHits();
 * </pre>
 * <p>results in an HTTP request like:</p>
 * <pre>
 * GET /-/bang/?p=hits&amp;s=mvrgdn3rvq7qmy HTTP/1.1
 * Host: vsci.hpl.hp.com
 * 
 * </pre>
 * <p>If the request is processed successfully, the <code>hits</code> promise
 * will eventually resolve to an {@link java.lang.Integer} indicating the number
 * of times the drum has been banged. On the other hand, should there be an
 * application level problem with the request, such as attempting to access a
 * drum which no longer exists, the <code>hits</code> promise will eventually
 * transition to the rejected state with a
 * {@linkplain org.ref_send.promise.Rejected#reason reason}
 * {@link java.lang.Exception} providing details about the problem. For example:</p>
 * <pre>
 * final int expected = &hellip;   // the expected number of hits
 * class Was extends Do&lt;Integer,Boolean&gt; implements Serializable {
 *     static public final int serialVersionUID = 1L;
 * 
 *     public Boolean
 *     fulfill(final Integer reported) {
 *         &hellip;    // compare reported value to expected value
 *     }
 * 
 *     public Boolean
 *     reject(final Exception reason) {
 *         if (reason instanceof {@link org.web_send.Failure}) {
 *             // HTTP request failed
 *             final Failure e = (Failure)reason;
 *             if ("410".equals(e.status)) {
 *                 // the target drum no longer exists!
 *                 &hellip;
 *             }
 *             &hellip;
 *         }
 *         &hellip;
 *     }
 * }
 * &hellip; = _.when(hits, new Was());
 * </pre>
 * <h3><a name="entity" href="#entity">Using a custom Content-Type</a></h3>
 * <p>By default, all invocation arguments and return values are represented as
 * <a href="http://json.org/">JSON</a> in HTTP requests and responses.
 * Sometimes, an application may need to use another Content-Type to interact
 * with a specific client, or to transfer raw bytes. This can be accomplished
 * using an {@link org.web_send.Entity}. For example, the following code:</p>
 * <pre>
 * public {@link org.web_send.Entity}
 * getGreeting() {
 *     return new Entity(
 *         "text/plain; charset=US-ASCII",
 *         {@link org.joe_e.array.ByteArray}.array({@link org.joe_e.charset.ASCII}.encode("Hello World!\n"))
 *     );
 * }
 * </pre>
 * <p>returns the following HTTP response to a <code>GET</code> request:</p>
 * <pre>
 * HTTP/1.1 200 OK
 * Content-Type: text/plain; charset=US-ASCII
 * Content-Length: 13
 * 
 * Hello World!
 * </pre>
 */
@org.joe_e.verified package org.web_send;