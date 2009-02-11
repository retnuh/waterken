// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.joe_e.array.PowerlessArray.builder;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ArrayBuilder;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.reflect.Reflection;
import org.ref_send.data.Interpreted;
import org.ref_send.data.Query;
import org.ref_send.data.Update;
import org.ref_send.promise.Eventual;
import org.ref_send.promise.Fulfilled;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.Task;
import org.ref_send.promise.Volatile;
import org.ref_send.scope.Layout;
import org.ref_send.scope.Scope;
import org.waterken.http.Message;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.http.Server;
import org.waterken.io.FileType;
import org.waterken.syntax.BadSyntax;
import org.waterken.syntax.json.JSONDeserializer;
import org.waterken.syntax.json.JSONSerializer;
import org.waterken.uri.Header;

/**
 * Server-side of the web-key protocol.
 */
/* package */ final class
Callee extends Struct implements Serializable {
    static private final long serialVersionUID = 1L;
    
    private final HTTP.Exports exports;

    protected
    Callee(final HTTP.Exports exports) {
        this.exports = exports;
    }

    // org.waterken.http.Server interface

    protected Message<Response>
    run(final String query, final Message<Request> m) throws Exception {
        
        // further dispatch the request based on the accessed member
        final String p = HTTP.predicate(query);
        if (null == p || ".".equals(p)) {       // introspection or when block
            if ("OPTIONS".equals(m.head.method)) {
                return new Message<Response>(
                    Response.options("TRACE","OPTIONS","GET","HEAD"), null);
            }
            if (!("GET".equals(m.head.method) || "HEAD".equals(m.head.method))){
                return new Message<Response>(
                    Response.notAllowed("TRACE","OPTIONS","GET","HEAD"), null);
            }
            Object value;
            try {
                // AUDIT: call to untrusted application code
                value = Eventual.promised(exports.reference(query)).cast();
            } catch (final NullPointerException e) {
                return serialize(m.head.method, "404", "not yet",
                                 Server.ephemeral, new Rejected<Object>(e));
            } catch (final Exception e) {
                value = new Rejected<Object>(e);
            }
            if (null == p && !HTTP.isPBC(value)) {
                value = describe(value.getClass());
            }
            final Response failed = m.head.allow("\"\"");
            if (null != failed) { return new Message<Response>(failed, null); }
            return serialize(m.head.method, "200", "OK", Server.forever, value);
        }                                       // member access

        // determine the target object
        final Object target;
        try {
            final Volatile<?> subject =
                Eventual.promised(exports.reference(query));
            // to preserve message order, only access members on a fulfilled ref
            // AUDIT: call to untrusted application code
            target = ((Fulfilled<?>)subject).cast();
        } catch (final Exception e) {
            return serialize(m.head.method, "404", "never", Server.forever,
                             new Rejected<Object>(e));
        }       
        if (HTTP.isPBC(target)) {
            // prevent access to local implementation details
            return new Message<Response>(Response.gone(), null);
        }
        
        if (target instanceof Query &&
                ("GET".equals(m.head.method) || "HEAD".equals(m.head.method))) {
            Object value;
            try {
                value = ((Query<?>)target).get(p);
            } catch (final Exception e) {
                value = new Rejected<Object>(e);
            }
            final String etag = exports.getTransactionTag();
            final Response failed = m.head.allow(etag);
            if (null != failed) {return new Message<Response>(failed,null);}
            Message<Response> r = serialize(m.head.method, "200", "OK",
                                            Server.ephemeral, value);
            if (!"".equals(etag)) {
                r = new Message<Response>(r.head.with("ETag",etag), r.body);
            }
            return r;
        }
        if (target instanceof Update && "POST".equals(m.head.method)) {
            final Response failed = m.head.allow(null);
            if (null != failed) { return new Message<Response>(failed, null); }
            final Method lambda = HTTP.dispatch(target, "run");
            final Object value= exports.execute(query,lambda,new Task<Object>(){
                public @SuppressWarnings("unchecked") Object
                run() throws Exception {
                    /*
                     * SECURITY CLAIM: deserialize inside the once block to
                     * ensure application code cannot detect request replay by
                     * causing failed deserialization
                     */ 
                    final Object arg;
                    try {
                        arg = deserialize(m, ConstArray.array(
                            lambda.getGenericParameterTypes()[1])).get(0);
                    } catch (final BadSyntax e) {
                        /*
                         * strip out the parsing information to avoid leaking
                         * information to the application layer
                         */ 
                        throw (Exception)e.getCause();
                    }

                    // AUDIT: call to untrusted application code
                    return ((Update<Object,Object>)target).run(p, arg);
                }
            });
            return serialize(m.head.method,"200","OK", Server.ephemeral, value);
        }
        if (target instanceof Interpreted) {
            PowerlessArray<String> methods =
                PowerlessArray.array("TRACE", "OPTIONS");
            if (target instanceof Query) { methods = methods.with("GET"); }
            if (target instanceof Update){ methods = methods.with("POST");}
            final String[] allow = methods.toArray(new String[0]);
            return new Message<Response>("OPTIONS".equals(m.head.method) ?
                Response.options(allow) : Response.notAllowed(allow), null);
        }
        
        // determine the type of accessed member
        final Method lambda = HTTP.dispatch(target, p);
        if (null == lambda) {                   // no such member
            if ("OPTIONS".equals(m.head.method)) {
                return new Message<Response>(
                    Response.options("TRACE", "OPTIONS"), null);
            }
            return new Message<Response>(
                    Response.notAllowed("TRACE", "OPTIONS"), null);
        }
        
        if (null != HTTP.property(lambda)) {    // property access
            if ("OPTIONS".equals(m.head.method)) {
                return new Message<Response>(
                    Response.options("TRACE","OPTIONS","GET","HEAD"), null);
            }
            if (!("GET".equals(m.head.method) || "HEAD".equals(m.head.method))){
                return new Message<Response>(
                    Response.notAllowed("TRACE","OPTIONS","GET","HEAD"), null);
            }
            Object value;
            try {
                // AUDIT: call to untrusted application code
                value = Reflection.invoke(bubble(lambda), target);
            } catch (final Exception e) {
                value = new Rejected<Object>(e);
            }
            final boolean constant = "getClass".equals(lambda.getName());
            final int maxAge = constant ? Server.forever : Server.ephemeral; 
            final String etag = constant ? "" : exports.getTransactionTag();
            final Response failed = m.head.allow(etag);
            if (null != failed) { return new Message<Response>(failed, null); }
            Message<Response> r =
                serialize(m.head.method, "200", "OK", maxAge, value);
            if (!"".equals(etag)) {
                r = new Message<Response>(r.head.with("ETag", etag), r.body);
            }
            return r;
        }
        
        if ("OPTIONS".equals(m.head.method)) {    // method invocation
            return new Message<Response>(
                Response.options("TRACE", "OPTIONS", "POST"), null);
        }
        if (!"POST".equals(m.head.method)) {
            return new Message<Response>(
                Response.notAllowed("TRACE", "OPTIONS", "POST"), null);
        }
        final Response failed = m.head.allow(null);
        if (null != failed) { return new Message<Response>(failed, null); }
        final Object value = exports.execute(query, lambda, new Task<Object>() {
            public Object
            run() throws Exception {
                /*
                 * SECURITY CLAIM: deserialize inside the once block to ensure
                 * application code cannot detect request replay by causing
                 * failed deserialization
                 */ 
                final ConstArray<?> argv;
                try {
                    argv = deserialize(m,
                        ConstArray.array(lambda.getGenericParameterTypes()));
                } catch (final BadSyntax e) {
                    /*
                     * strip out the parsing information to avoid leaking
                     * information to the application layer
                     */ 
                    throw (Exception)e.getCause();
                }

                // AUDIT: call to untrusted application code
                return Reflection.invoke(bubble(lambda), target,
                        argv.toArray(new Object[argv.length()]));
            }
        });
        return serialize(m.head.method, "200", "OK", Server.ephemeral, value);
    }
    
    static private final Class<?> Inline = Eventual.ref(0).getClass();
    
    private Message<Response>
    serialize(final String method, final String status, final String phrase,
              final int maxAge, Object value) throws Exception {
        if (Inline.isInstance(value)) {
            value = ((Fulfilled<?>)value).cast();
        }
        final String contentType;
        final ByteArray content;
        if (value instanceof ByteArray) {
            contentType = FileType.unknown.name;
            content = (ByteArray)value;
        } else {
            contentType = FileType.json.name;
            content = new JSONSerializer().run(exports.export(),
                                               ConstArray.array(value));
        }
        if ("POST".equals(method)) {
            return new Message<Response>(new Response(
                "HTTP/1.1", status, phrase,
                PowerlessArray.array(
                    new Header("Content-Type", contentType),
                    new Header("Content-Length", "" + content.length())
                )), content);
        }
        return new Message<Response>(new Response(
            "HTTP/1.1", status, phrase,
            PowerlessArray.array(
                new Header("Cache-Control", 
                           0 < maxAge ? "max-age=" + maxAge : "no-cache"),
                new Header("Content-Type", contentType),
                new Header("Content-Length", "" + content.length())
            )),
            "HEAD".equals(method) ? null : content);
    }
    
    private ConstArray<?>
    deserialize(final Message<Request> m,
                final ConstArray<Type> parameters) throws Exception {
        if (Header.equivalent(FileType.unknown.name, m.head.getContentType())) {
            return ConstArray.array(m.body);
        }
        return new JSONDeserializer().run(exports.getHere(), exports.connect(),
                parameters, exports.getCodebase(), m.body.asInputStream());
    }

    /**
     * Finds the first invocable declaration of a public method.
     */
    static private Method
    bubble(final Method method) {
        final Class<?> declarer = method.getDeclaringClass();
        if (Object.class == declarer || Struct.class == declarer) {return null;}
        if (isPublic(declarer.getModifiers())) { return method; }
        if (isStatic(method.getModifiers())) { return null; }
        final String name = method.getName();
        final Class<?>[] param = method.getParameterTypes();
        for (final Class<?> i : declarer.getInterfaces()) {
            try {
                final Method r = bubble(Reflection.method(i, name, param));
                if (null != r) { return r; }
            } catch (final NoSuchMethodException e) {}
        }
        final Class<?> parent = declarer.getSuperclass();
        if (null != parent) {
            try {
                final Method r = bubble(Reflection.method(parent, name, param));
                if (null != r) { return r; }
            } catch (final NoSuchMethodException e) {}
        }
        return null;
    }
    
    static private Scope
    describe(final Class<?> type) {
        final Object ts = types(type);
        return new Scope(new Layout(PowerlessArray.array("$")),
                         ConstArray.array(ts));
    }
    
    /**
     * Enumerate all types implemented by a class.
     */
    static private PowerlessArray<String>
    types(final Class<?> actual) {
        final Class<?> end =
            Struct.class.isAssignableFrom(actual) ? Struct.class : Object.class;
        final PowerlessArray.Builder<String> r = builder(4);
        for (Class<?> i=actual; end!=i; i=i.getSuperclass()) { ifaces(i, r); }
        return r.snapshot();
    }

    /**
     * List all the interfaces implemented by a class.
     */
    static private void
    ifaces(final Class<?> type, final ArrayBuilder<String> r) {
        if (type == Serializable.class) { return; }
        if (Modifier.isPublic(type.getModifiers())) {
            try { r.append(Reflection.getName(type).replace('$', '-')); }
            catch (final Exception e) {}
        }
        for (final Class<?> i : type.getInterfaces()) { ifaces(i, r); }
    }
}
