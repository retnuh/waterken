// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import java.io.Serializable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.reflect.Reflection;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.Volatile;
import org.ref_send.promise.eventual.Channel;
import org.ref_send.promise.eventual.Compose;
import org.ref_send.promise.eventual.Do;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.promise.eventual.Failure;
import org.ref_send.promise.eventual.Invoke;
import org.ref_send.promise.eventual.Resolver;
import org.ref_send.type.Typedef;
import org.waterken.http.Message;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.io.FileType;
import org.waterken.remote.Messenger;
import org.waterken.remote.Remote;
import org.waterken.syntax.BadSyntax;
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
Caller extends Struct implements Messenger, Serializable {
    static private final long serialVersionUID = 1L;

    private final Eventual _;           // eventual operator
    private final HTTP.Exports exports; // operations on web-keys
    private final Pipeline msgs;        // queued HTTP requests
    
    protected
    Caller(final Eventual _, final HTTP.Exports exports, final Pipeline msgs) {
        this._ = _;
        this.exports = exports;
        this.msgs = msgs;
    }

    // org.waterken.remote.Messenger interface

    public void
    when(final String href, final Remote proxy, final Do<Object,?> observer) {
        class When extends Struct implements Operation, Serializable {
            static private final long serialVersionUID = 1L;

            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                final String target = HTTP.get(href, ".");
                final String authority = URI.authority(target);
                final String location = Authority.location(authority);
                return new Message<Request>(new Request(
                    "HTTP/1.1", "GET", URI.request(target),
                    PowerlessArray.array(
                        new Header("Host", location)
                    )), null);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                final Member member;
                try {
                    member = Reflection.method(When.class, "fulfill",
                                               String.class, Message.class);
                } catch (final Exception e) { throw new Error(e); }
                _.log.got(request, member);
                Volatile<Object> value;
                try {
                    value = Eventual.promised(deserialize(href,
                        response, Compose.parameter(observer)));
                } catch (final Exception e) {
                    value = new Rejected<Object>(e);
                }
                _.when(value, observer);
            }
            
            public void
            reject(final String request, final Exception reason) {
                final Member member;
                try {
                    member = Reflection.method(When.class, "reject",
                                               String.class, Exception.class);
                } catch (final Exception e) { throw new Error(e); }
                _.log.got(request, member);
                _.when(new Rejected<Object>(reason), observer);
            }
        }
        _.log.sent(msgs.enqueue(new When()));
    }
   
    public Object
    invoke(final String href, final Object proxy,
           final Method method, final Object... arg) {
        final ConstArray<?> argv= ConstArray.array(null==arg?new Object[0]:arg);
        return null != HTTP.property(method)
            ? get(href, proxy.getClass(), method, argv)
        : post(href, proxy.getClass(), method, argv);
    }
    
    private Object
    get(final String href, final Class<?> type,
        final Method method, final ConstArray<?> argv) {
        final Channel<Object> r = _.defer();
        final Resolver<Object> resolver = r.resolver;
        class GET extends Struct implements Operation, Query, Serializable {
            static private final long serialVersionUID = 1L;

            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                final String target = HTTP.get(href, HTTP.property(method));
                final String authority = URI.authority(target);
                final String location = Authority.location(authority);
                return new Message<Request>(new Request(
                    "HTTP/1.1", "GET", URI.request(target),
                    PowerlessArray.array(
                        new Header("Host", location)
                    )), null);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                _.log.got(request, method);
                receive(href, response, type, method, argv, resolver);
            }
            
            public void
            reject(final String request, final Exception reason) {
                _.log.got(request, method);
                resolver.reject(reason);
            }
        }
        _.log.sent(msgs.enqueue(new GET()));
        return _.cast(Typedef.raw(Typedef.bound(method.getGenericReturnType(),
                                                type)), r.promise);
    }
    
    private Object
    post(final String href, final Class<?> type,
         final Method method, final ConstArray<?> argv) {
        final Object r_;
        final Resolver<Object> resolver;
        final Class<?> R =
            Typedef.raw(Typedef.bound(method.getGenericReturnType(), type));
        if (void.class == R || Void.class == R) {
            r_ = null;
            resolver = null;
        } else {
            final Channel<Object> x = _.defer();
            r_ = _.cast(R, x.promise);
            resolver = x.resolver;
        }
        class POST extends Struct implements Operation, Update, Serializable {
            static private final long serialVersionUID = 1L;
            
            public Message<Request>
            render(final String x, final long w, final int m) throws Exception {
                return serialize(HTTP.post(href, method.getName(),x,w,m), argv);
            }

            public void
            fulfill(final String request, final Message<Response> response) {
                _.log.got(request + "-return", method);
                receive(href, response, type, method, argv, resolver);
            }
            
            public void
            reject(final String request, final Exception reason) {
                _.log.got(request + "-return", method);
                if (null != resolver) { resolver.reject(reason); }
            }
        }
        _.log.sent(msgs.enqueue(new POST()));
        return r_;
    }
    
    private void
    receive(final String href, final Message<Response> response,
            final Class<?> type, final Method method,
            final ConstArray<?> argv, final Resolver<Object> resolver) {
        if ("404".equals(response.head.status)) {
            // re-dispatch this invocation on the resolved value of the web-key
            final Invoke<Object> invoke = new Invoke<Object>(method, argv);
            final Do<Object,?> forward = null == resolver
                ? invoke : new Compose<Object,Object>(invoke, resolver);
            Object x;
            try {
                x = exports.connect().run(href, null, Object.class);
            } catch (final Exception e) {
                x = new Rejected<Object>(e);
            }
            _.when(x, forward);
        } else if (null != resolver) {
            Volatile<Object> value;
            try {
                value = Eventual.promised(deserialize(href, response,
                    Typedef.bound(method.getGenericReturnType(), type)));
            } catch (final BadSyntax e) {
                /*
                 * strip out the parsing information to avoid leaking
                 * information to the application layer
                 */ 
                value = new Rejected<Object>((Exception)e.getCause());
            } catch (final Exception e) {
                value = new Rejected<Object>(e);
            }
            resolver.resolve(value);
        }
    }
    
    private Message<Request>
    serialize(final String target, final ConstArray<?> argv) throws Exception {
        final String authority = URI.authority(target);
        final String location = Authority.location(authority);
        if (argv.length() == 1 && argv.get(0) instanceof ByteArray) {
            final ByteArray body = (ByteArray)argv.get(0);
            return new Message<Request>(new Request(
                "HTTP/1.1", "POST", URI.request(target),
                PowerlessArray.array(
                    new Header("Host", location),
                    new Header("Content-Type", FileType.unknown.name),
                    new Header("Content-Length", "" + body.length())
                )), body);        
        }
        final ByteArray body = new JSONSerializer().run(
                exports.send(URI.resolve(target, ".")), argv);  
        return new Message<Request>(new Request(
            "HTTP/1.1", "POST", URI.request(target),
            PowerlessArray.array(
                new Header("Host", location),
                new Header("Content-Type", FileType.json.name),
                new Header("Content-Length", "" + body.length())
            )), body);        
    }
    
    private Object
    deserialize(final String base,
                final Message<Response> m, final Type R) throws Exception {
        final Importer connect = exports.connect();
        if ("200".equals(m.head.status) || "201".equals(m.head.status) ||
            "202".equals(m.head.status) || "203".equals(m.head.status)) {
            if (Header.equivalent(FileType.unknown.name,
                                  m.head.getContentType())) {
                return ConstArray.array(m.body);
            }
            return new JSONDeserializer().run(base,connect, ConstArray.array(R),
                    exports.getCodebase(), m.body.asInputStream()).get(0);
        } 
        if ("204".equals(m.head.status) ||
            "205".equals(m.head.status)) { return null; }
        if ("303".equals(m.head.status)) {
            for (final Header h : m.head.headers) {
                if (Header.equivalent("Location", h.name)) {
                    return connect.run(h.value, null, R);
                }
            }
            return null;    // request accepted, but no response provided
        } 
        throw new Failure(m.head.status, m.head.phrase);
    }
}
