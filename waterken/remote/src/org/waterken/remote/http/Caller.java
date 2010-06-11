// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.ref_send.promise.Deferred;
import org.ref_send.promise.Eventual;
import org.ref_send.promise.Failure;
import org.ref_send.promise.Resolver;
import org.ref_send.type.Typedef;
import org.waterken.http.Message;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.io.FileType;
import org.waterken.syntax.BadSyntax;
import org.waterken.syntax.Exporter;
import org.waterken.syntax.Importer;
import org.waterken.syntax.json.JSONDeserializer;
import org.waterken.syntax.json.JSONSerializer;
import org.waterken.uri.Authority;
import org.waterken.uri.Header;
import org.waterken.uri.URI;

/**
 * Client-side of the HTTP web-amp protocol.
 */
/* package */ final class
Caller extends Struct implements Serializable {
    static private final long serialVersionUID = 1L;

    private final Eventual _;
    private final String here;
    private final ClassLoader codebase;
    private final Importer connect;
    private final Exporter export;
    private final Pipeline msgs;        // queued HTTP requests
    
    protected
    Caller(final Eventual _, final String here, final ClassLoader codebase,
           final Importer connect, final Exporter export, final Pipeline msgs) {
        this._ = _;
        this.here = here;
        this.codebase = codebase;
        this.connect = connect;
        this.export = export;
        this.msgs = msgs;
    }

    // org.waterken.remote.http.Caller interface

    public void
    when(final String href, final Class<?> T, final Resolver<Object> resolver) {
        class When extends Operation implements Serializable {
            static private final long serialVersionUID = 1L;
            
            When() { super(false, false); }

            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                final String requestURI = HTTP.get(URI.resolve(here,href),null);
                return new Message<Request>(new Request(
                    "HTTP/1.1", "GET", URI.request(requestURI),
                    PowerlessArray.array(
                        new Header("Host",
                            Authority.location(URI.authority(requestURI)))
                    )), null);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                _.log.got(request, null, null);
                if ("404".equals(response.head.status)) {
                    resolver.progress();
                } else {
                    resolver.apply(receive(
                        HTTP.get(URI.resolve(here, href), null), response, T));
                }
            }
            
            public void
            reject(final String request, final Exception reason) {
                _.log.got(request, null, null);
                resolver.reject(reason);
            }
        }
        _.log.sent(msgs.poll(new When()));
    }
   
    public Object
    invoke(final String href, final Object proxy,
           final Method method, final Object... arg) {
        final Deferred<Object> r = _.defer();
        final String property = Dispatch.property(method);
        if (null != property) {
            get(href, property, proxy, method, r.resolver);
        } else {
            final Class<?> Fulfilled = Eventual.ref(0).getClass();
            final ConstArray.Builder<Object> argv =
                ConstArray.builder(null != arg ? arg.length : 0);
            for (final Object x : null != arg ? arg : new Object[0]) {
                argv.append(Fulfilled.isInstance(x) ? Eventual.near(x) : x);
            }
            post(href,method.getName(),proxy,method,argv.snapshot(),r.resolver);
            // TODO: implement pipeline references?
        }
        return Eventual.cast(Typedef.raw(Typedef.bound(
                method.getGenericReturnType(), proxy.getClass())), r.promise);
    }
    
    private void
    get(final String href, final String name, final Object proxy,
            final Method method, final Resolver<Object> resolver) {
        class GET extends Operation implements Serializable {
            static private final long serialVersionUID = 1L;
            
            GET() { super(true, false); }

            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                final String requestURI = HTTP.get(URI.resolve(here,href),name);
                return new Message<Request>(new Request(
                    "HTTP/1.1", "GET", URI.request(requestURI),
                    PowerlessArray.array(
                        new Header("Host",
                            Authority.location(URI.authority(requestURI)))
                    )), null);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                _.log.got(request, null, null);
                if (null != resolver) {
                  resolver.apply(receive(HTTP.get(URI.resolve(here,href),name),
                    response, Typedef.bound(method.getGenericReturnType(),
                                    		proxy.getClass())));
                }
            }
            
            public void
            reject(final String request, final Exception reason) {
                _.log.got(request, null, null);
                if (null != resolver) { resolver.reject(reason); }
            }
        }
        _.log.sent(msgs.enqueue(new GET()));
    }
    
    private void
    post(final String href, final String name,
         final Object proxy, final Method method,
         final ConstArray<?> argv, final Resolver<Object> resolver) {
        class POST extends Operation implements Serializable {
            static private final long serialVersionUID = 1L;
            
            POST() { super(false, true); }
            
            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                return serialize(here, export,
                    HTTP.post(URI.resolve(here, href), name, x, w, m),
                    ConstArray.array(method.getGenericParameterTypes()), argv);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                final Type R = Typedef.bound(method.getGenericReturnType(),
                                             proxy.getClass());
                final Object r = receive(HTTP.post(URI.resolve(here,href), name,
                					               null, 0, 0), response, R);
                final boolean got = null!=r || (void.class!=R && Void.class!=R); 
                if (got) { _.log.got(request + "-return", null, null); }
                if (null != resolver && got) { resolver.apply(r); }
            }
            
            public void
            reject(final String request, final Exception reason) {
                _.log.got(request, null, null);
                if (null != resolver) { resolver.reject(reason); }
            }
        }
        _.log.sent(msgs.enqueue(new POST()));
    }
    
    private Object
    receive(final String base, final Message<Response> m, final Type R) {
        try {
            for (final Header header : m.head.headers) {
                if (Header.equivalent("Warning", header.name)) {
                    throw new Warning();
                }
            }
            if ("200".equals(m.head.status) || "201".equals(m.head.status) ||
                "202".equals(m.head.status) || "203".equals(m.head.status)) {
                String contentType = m.head.getContentType();
                if (null == contentType) {
                    contentType = FileType.json.name;
                } else {
                    final int end = contentType.indexOf(';');
                    if (-1 != end) {
                        contentType = contentType.substring(0, end);
                    }
                }
                return Header.equivalent(FileType.unknown.name, contentType) ?
                    m.body : new JSONDeserializer().deserialize(
                        m.body.asInputStream(), connect, base, codebase, R);
            } 
            if ("204".equals(m.head.status) ||
                "205".equals(m.head.status)) { return true; }
            if ("303".equals(m.head.status)) {
                for (final Header h : m.head.headers) {
                    if (Header.equivalent("Location", h.name)) {
                        return connect.apply(h.value, base, R);
                    }
                }
            } 
            throw new Failure(m.head.status, m.head.phrase);
        } catch (final BadSyntax e) {
            /*
             * strip out the parsing information to avoid leaking
             * information to the application layer
             */ 
            return Eventual.reject((Exception)e.getCause());
        } catch (final Exception e) {
            return Eventual.reject(e);
        }
    }
    
    static protected Message<Request>
    serialize(final String here, final Exporter export, final String requestURI,
              final ConstArray<Type> types,
              final ConstArray<?> argv) throws Exception {
        final String contentType;
        final ByteArray content;
        if (argv.length() == 1 && argv.get(0) instanceof ByteArray) {
            contentType = FileType.unknown.name;
            content = (ByteArray)argv.get(0);
        } else {
            contentType = FileType.json.name;
            content = new JSONSerializer().serializeTuple(
                HTTP.changeBase(here, export, URI.resolve(requestURI, ".")),
                types, argv);
        }
        return new Message<Request>(new Request(
            "HTTP/1.1", "POST", URI.request(requestURI),
            PowerlessArray.array(
              new Header("Host", Authority.location(URI.authority(requestURI))),
              new Header("Content-Type", contentType),
              new Header("Content-Length", "" + content.length())
            )), content);        
    }
}
