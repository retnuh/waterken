// Copyright 2007-2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html

/**
 * A pass-by-construction interface.
 * <p>The ref_send API supports the design of interfaces that work well in a
 * distributed application. One of the differences between a local application
 * and a distributed application is the importance of pass-by-copy data. In a
 * local application, the difference between accessing a variable versus calling
 * a method may be negligible. In a distributed application, the difference may
 * be significant since an additional network request may be required for the
 * method invocation.  This network request negatively impacts performance since
 * an additional network round-trip is required. More importantly though, the
 * additional network request complicates the client code since it must now cope
 * with the timing issues and failure modes that come with doing a network
 * request.  Client code is often much simpler when all the data it needs can be
 * provided as one bundle. To support creating such data bundles, this package
 * defines an API for declaring a pass-by-construction class. When an object of
 * such a class is sent in a method invocation, or return, its publicly
 * accessible state is sent, instead of a remote reference. Similarly, such an
 * object can be reconstructed when its state is received as part of a method
 * invocation.</p>
 * <p>A class can be made pass-by-construction simply by declaring it to be so
 * and annotating one of its public constructors with information mapping the
 * class' public fields to its constructor parameters. For example:</p>
 * <pre>
 * package org.example.membership;
 * 
 * import org.joe_e.{@link org.joe_e.Struct};
 * import org.ref_send.{@link org.ref_send.deserializer};
 * import org.ref_send.{@link org.ref_send.name};
 * import org.ref_send.{@link org.ref_send.Record};
 * 
 * import java.io.{@link java.io.Serializable};
 * 
 * /**
 *  * Information about a club member.
 *  *&#47;
 * public class
 * LoyaltyCard extends Struct implements Record, Serializable {
 *     static private final long serialVersionUID = 1L;
 * 
 *     /**
 *      * member's full name
 *      *&#47;
 *     public final String name;
 * 
 *     /**
 *      * year membership began
 *      *&#47;
 *     public final int year;
 * 
 *     /**
 *      * Is a premium member?
 *      *&#47;
 *     public final boolean premium;
 * 
 *     /**
 *      * Constructs an instance.
 *      * &#64;param name    {&#64;link #name}
 *      * &#64;param year    {&#64;link #year}
 *      * &#64;param premium {&#64;link #premium}
 *      *&#47;
 *     public &#64;deserializer
 *     LoyaltyCard(&#64;name("name") final String name,
 *                 &#64;name("year") final int year,
 *                 &#64;name("premium") final boolean premium) {
 *         this.name = name;
 *         this.year = year;
 *         this.premium = premium;
 *     }
 * }
 * </pre>
 * <p>The Javadoc comments in the above example are purely optional. The
 * important steps are:</p>
 * <ul>
 *  <li>implement {@link org.ref_send.Record}</li>
 *  <li>implement {@link java.io.Serializable} and declare a
 *      <code>serialVersionUID</code></li>
 *  <li>declare each piece of data to be sent as a <code>public final</code>
 *      field</li>
 *  <li>annotate one public constructor with the
 *      {@link org.ref_send.deserializer} annotation</li>
 *  <li>annotate each parameter in the deserialization constructor with the
 *      {@linkplain org.ref_send.name} of the corresponding public field</li>
 *  <li>implement the deserialization constructor as a simple assignment of
 *      arguments to member fields</li>
 *  <li>extend the {@link org.joe_e.Struct} class, which provides default
 *      implementations for the {@link java.lang.Object}
 *      {@link java.lang.Object#equals equals()} and
 *      {@link java.lang.Object#hashCode hashCode()} methods that do a by-value
 *      comparision, instead of using the object's creation identity</li>
 * </ul>
 * <p>When using the Waterken Server, a remote invocation whose sole argument is
 * an instance of the class above results in an HTTP request with the following
 * <a href="http://json.org/">JSON</a> data:</p>
 * <pre>
 * [ {
 *   "$": [ "org.example.membership.LoyaltyCard" ],
 *   "name": "Tyler Close",
 *   "year": 2008,
 *   "premium": true
 *   } ]
 * </pre>
 * <p>The networking code in the Waterken Server automatically recognizes which
 * invocation arguments are {@link org.ref_send.Record} objects and produces the
 * corresponding JSON data for them.  You can create an arbitrarily large JSON
 * document by constructing an arbitrarily deep tree of
 * {@link org.ref_send.Record} objects. For example, the class below adds an
 * address and {@linkplain org.joe_e.array array} of tags:</p>
 * <pre>
 * package org.example.membership;
 * 
 * import org.joe_e.array.{@link org.joe_e.array.PowerlessArray};
 * import org.ref_send.{@link org.ref_send.deserializer};
 * import org.ref_send.{@link org.ref_send.name};
 * 
 * /**
 *  * Postal information about a club member.
 *  *&#47;
 * public class
 * MailingLoyaltyCard extends LoyaltyCard {
 *     static private final long serialVersionUID = 1L;
 * 
 *     /**
 *      * postal address
 *      *&#47;
 *     public final Address mailto;
 * 
 *     /**
 *      * a list of tags indicating mailing preferences
 *      *&#47;
 *     public final PowerlessArray&lt;String&gt; preferences;
 * 
 *     /**
 *      * Constructs an instance.
 *      * &#64;param name        {&#64;link #name}
 *      * &#64;param year        {&#64;link #year}
 *      * &#64;param premium     {&#64;link #premium}
 *      * &#64;param mailto      {&#64;link #mailto}
 *      * &#64;param preferences {&#64;link #preferences}
 *      *&#47;
 *     public &#64;deserializer
 *     MailingLoyaltyCard(
 *           &#64;name("name") final String name,
 *           &#64;name("year") final int year,
 *           &#64;name("premium") final boolean premium,
 *           &#64;name("mailto") final Address mailto,
 *           &#64;name("preferences") final PowerlessArray&lt;String&gt; preferences) {
 *         super(name, year, premium);
 *         this.mailto = mailto;
 *         this.preferences = preferences;
 *     }
 * }
 * </pre>
 * <p>An instance of the above class would produce JSON like:</p>
 * <pre>
 * [ {
 *   "$": [ "org.example.membership.MailingLoyaltyCard", "org.example.membership.LoyaltyCard" ],
 *   "name": "Tyler Close",
 *   "year": 2008,
 *   "premium": true,
 *   "mailto": {
 *     "street": "1501 Page Mill Road",
 *     "city": "Palo Alto",
 *     "state": "CA",
 *     "zip": "94304"
 *     },
 *   "preferences": [ "2-day-shipping", "monthly" ]
 *   } ]
 * </pre>
 * <p>The value of the <code>"$"</code> member is an array listing the name of
 * every type implemented by the object, ordered from most specific to least. If
 * the most specific type of an object is implied by the referring object, the
 * type declaration is omitted. For example, the object referred to by the
 * <code>"mailto"</code> member is not annotated with a <code>"$"</code> member,
 * since the type <code>org.example.membership.Address</code> is implied by the
 * referring object's type
 * <code>"org.example.membership.MailingLoyaltyCard"</code>.</p>
 * <p>By making effective use of {@link org.ref_send.Record} objects, you can
 * create distributed applications in the document-oriented messaging style. In
 * such applications, clients and servers coordinate primarily based on the
 * content of exchanged documents, rather than on expectations about remotely
 * maintained state. Such designs can sometimes result in fewer dependencies
 * between clients and servers and so facilitate the creation of interoperable
 * software. When using the ref_send API, you define the structure of your
 * exchanged documents by defining a set of {@link org.ref_send.Record} types.
 * Each exchanged document is then created by composing an object tree from
 * these {@link org.ref_send.Record} types.</p>
 */
@org.joe_e.IsJoeE package org.ref_send;