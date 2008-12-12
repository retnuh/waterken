// Copyright 2002-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.http;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;

import org.joe_e.Powerless;
import org.joe_e.Struct;
import org.joe_e.inert;
import org.joe_e.array.ByteArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.charset.ASCII;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.waterken.io.open.Open;
import org.waterken.uri.Header;

/**
 * An HTTP Request-Line and headers.
 */
public final class
Request extends Struct implements Powerless, Record, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * <code>HTTP-Version</code>
     */
    public final String version;

    /**
     * <code>Request-Line</code> <code>Method</code>
     */
    public final String method;

    /**
     * <code>Request-URI</code>
     */
    public final String URI;

    /**
     * each header: [ name =&gt; line ]
     */
    public final PowerlessArray<Header> headers;

    /**
     * Constructs an instance.
     * @param version   {@link #version}
     * @param method    {@link #method}
     * @param URI       {@link #URI}
     * @param headers   {@link #headers}
     * @param body      {@link #body}
     */
    public @deserializer
    Request(@name("version") final String version,
            @name("method") final String method,
            @name("URI") final String URI,
            @name("headers") final PowerlessArray<Header> headers) {
        this.version = version;
        this.method = method;
        this.URI = URI;
        this.headers = headers;
    }
    
    // org.waterken.http.Request interface

    /**
     * Gets the <code>Content-Type</code>.
     * @return specified Media-Type, or <code>null</code> if unspecified
     */
    public String
    getContentType() { return TokenList.find(null, "Content-Type", headers); }

    /**
     * Gets the <code>Content-Length</code>.
     * @return number of bytes expected, or <code>-1</code> if unspecified
     */
    public int
    getContentLength() {
        final String sSize = TokenList.find(null, "Content-Length", headers);
        if (null == sSize) { return -1; }
        final int nSize = Integer.parseInt(sSize);
        if (nSize < 0) { throw new NumberFormatException(); }
        return nSize;
    }

    /**
     * Outputs the encoding of the message header.
     * @param out   binary output stream
     * @throws IOException  any I/O problem
     */
    public void
    writeTo(final OutputStream out) throws IOException {
        final Writer hout = ASCII.output(Open.output(out));

        // output the Request-Line
        hout.write(method);
        hout.write(" ");
        hout.write(URI);
        hout.write(" ");
        hout.write(version);
        hout.write("\r\n");

        // output the header
        for (final Header header : headers) {
            hout.write(header.name);
            hout.write(": ");
            hout.write(header.value);
            hout.write("\r\n");
        }
        hout.write("\r\n");
        hout.flush();
        hout.close();
    }

    /**
     * Calls {@link #expect expect} and then {@link #allow allow}.
     * @param etag      corresponding response entity tag,
     *                  or <code>null</code> if no current entity exists
     * @param respond   corresponding response processor
     * @param allow     allowed HTTP methods
     * @return <code>true</code> if processing should continue,
     *         else <code>false</code>
     * @throws Exception    any problem
     */
    public @inert boolean
    respond(final String etag, final Client client,
            final String... allow) throws Exception {
        return expect(client, allow) && allow(etag, client, allow);
    }
    
    /**
     * Handles unexpected requests.
     * <p>
     * A request processor should begin by calling this method and only
     * proceed if <code>true</code> is returned.
     * </p>
     * <p>
     * The implementation handles any <code>Expect</code> header.
     * </p>
     * @param respond   corresponding response processor
     * @param allow     allowed HTTP methods
     * @return <code>true</code> if processing should continue,
     *         else <code>false</code>
     * @throws Exception    any problem
     */
    public @inert boolean
    expect(final Client client, final String... allow) throws Exception {
        
        // filter for disallowed methods
        boolean allowed = false;
        for (final String verb : allow) {
            if (method.equals(verb)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            client.receive(notAllowed(allow), null);
            return false;
        }
        
        // process any Expect header
        for (final Header header : headers) {
            if (TokenList.equivalent("Expect", header.name)) {
                if (TokenList.equivalent("100-continue", header.value) &&
                    !(version.equals("HTTP/1.0") ||
                      version.startsWith("HTTP/0."))) {
                    client.receive(new Response("HTTP/1.1", "100", "Continue",
                        PowerlessArray.array(new Header[] {})), null);
                } else {
                    client.receive(new Response(
                        "HTTP/1.1", "417", "Expectation Failed",
                        PowerlessArray.array(
                            new Header("Content-Length", "0")
                        )), null);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Handles preconditions on access to a particular resource.
     * @param etag      corresponding response entity tag,
     *                  or <code>null</code> if no current entity exists
     * @param respond   corresponding response processor
     * @param allow     allowed HTTP methods
     * @return <code>true</code> if processing should continue,
     *         else <code>false</code>
     * @throws Exception    any problem
     */
    public @inert boolean
    allow(final String etag, final Client client,
          final String... allow) throws Exception {
        // check that no conditions prevent the supported method from executing
        if (null != etag) {
            final String ifNoneMatch = TokenList.list("If-None-Match", headers);
            if ("*".equals(ifNoneMatch) || -1 != ifNoneMatch.indexOf(etag)) {
                if ("GET".equals(method) || "HEAD".equals(method)) {
                    client.receive(new Response(
                        "HTTP/1.1", "304", "Not Modified",
                        PowerlessArray.array(
                            new Header("ETag", etag)
                        )), null);
                } else {
                    client.receive(new Response(
                        "HTTP/1.1", "412", "Precondition Failed",
                        PowerlessArray.array(
                            new Header("Content-Length", "0")
                        )), null);
                }
                return false;
            }
        }
        final String ifMatch = TokenList.list("If-Match", headers);
        if (!"".equals(ifMatch)) {
            if (null == etag || (-1 == ifMatch.indexOf(etag) &&
                                 !"*".equals(ifMatch))) {
                client.receive(new Response(
                    "HTTP/1.1", "412", "Precondition Failed",
                    PowerlessArray.array(
                        new Header("Content-Length", "0")
                    )), null);
                return false;
            }
        }
        
        // proceed with any positive acknowledgments
        if ("TRACE".equals(method)) {
            final Message<Response> r = trace();
            client.receive(r.head, r.body.asInputStream());
            return false;
        }
        if ("OPTIONS".equals(method)) {
            client.receive(options(allow), null);
            return false;
        }
        return true;
    }
    
    /**
     * Constructs a <code>TRACE</code> response.
     */
    public @inert Message<Response>
    trace() {
        final ByteArray.BuilderOutputStream buffer =
            ByteArray.builder().asOutputStream();
        try {
            writeTo(buffer);
        } catch (final IOException e) { throw new RuntimeException(e); }
        final ByteArray content = buffer.snapshot();
        return new Message<Response>(new Response(
            "HTTP/1.1", "200", "OK",
            PowerlessArray.array(
                new Header("Content-Length", "" + content.length()),
                new Header("Content-Type", "message/http; charset=iso-8859-1")
            )), content);
    }

    /**
     * Constructs an <code>OPTIONS</code> response.
     * @param allow supported HTTP methods
     */
    static public Response
    options(final String... allow) {
        return new Response(
            "HTTP/1.1", "204", "OK",
            PowerlessArray.array(
                new Header("Allow", TokenList.encode(allow))
            ));
    }
    
    /**
     * Constructs a 405 Method Not Allowed response.
     * @param allow supported HTTP methods
     */
    static public Response
    notAllowed(final String... allow) {
        return new Response(
            "HTTP/1.1", "405", "Method Not Allowed",
            PowerlessArray.array(
                new Header("Allow", TokenList.encode(allow)),
                new Header("Content-Length", "0")
            ));
    }
}
