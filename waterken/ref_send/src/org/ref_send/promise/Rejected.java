// Copyright 2002-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.ref_send.promise;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.joe_e.Powerless;
import org.joe_e.Selfless;
import org.joe_e.reflect.Proxies;
import org.joe_e.reflect.Reflection;
import org.ref_send.type.Typedef;

/**
 * A rejected promise.
 * @param <T> referent type
 */
/* package */ final class
Rejected<T> implements Promise<T>, InvocationHandler, Powerless,
                       Selfless, Serializable {
    static private final long serialVersionUID = 1L;
    
    /**
     * reason for rejecting the promise
     */
    public final Exception reason;

    /**
     * Construct an instance.
     * @param reason    {@link #reason}
     */
    public
    Rejected(final Exception reason) {
        this.reason = reason;
    }
    
    // java.lang.Object interface
    
    /**
     * Is the given object the same?
     * @param x compared to object
     * @return <code>true</code> if the same, else <code>false</code>
     */
    public boolean
    equals(final Object x) {
        return x instanceof Rejected<?> &&
               (null != reason
                   ? reason.equals(((Rejected<?>)x).reason)
                   : null == ((Rejected<?>)x).reason);
    }
    
    /**
     * Calculates the hash code.
     */
    public int
    hashCode() { return 0xDEADBEA7; }

    // org.ref_send.promise.Promise interface

    /**
     * Throws the {@link #reason}.
     * @throws  Exception   {@link #reason}
     */
    public T
    call() throws Exception { throw reason; }

    // java.lang.reflect.InvocationHandler interface

    /**
     * Forwards a Java language invocation.
     * @param proxy     eventual reference
     * @param method    method to invoke
     * @param args      invocation arguments
     * @return {@link Eventual#cast cast} of <code>this</code>
     * @throws Exception    problem invoking an {@link Object} method
     * @throws Error        <code>method</code> return cannot be cast to
     */
    public Object
    invoke(final Object proxy, final Method method,
           final Object[] args) throws Exception {
        if (Object.class == method.getDeclaringClass()) {
            if ("equals".equals(method.getName())) {
                return args[0] instanceof Proxy &&
                    proxy.getClass() == args[0].getClass() &&
                    equals(Proxies.getHandler((Proxy)args[0]));
            } else {
                return Reflection.invoke(method, this, args);
            }
        }
        try {
            return Eventual.cast(Typedef.raw(Typedef.bound(
                    method.getGenericReturnType(), proxy.getClass())), this);
        } catch (final Exception e) { throw new Error(e); }
    }
}
