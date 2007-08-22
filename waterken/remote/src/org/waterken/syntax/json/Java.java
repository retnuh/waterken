// Copyright 2002-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.joe_e.Struct;
import org.joe_e.Token;
import org.joe_e.array.PowerlessArray;
import org.joe_e.reflect.Reflection;
import org.waterken.id.Exporter;
import org.waterken.id.Importer;
import org.waterken.uri.Path;
import org.waterken.uri.URI;

/**
 * Java &lt;=&gt; JSON naming conventions.
 */
public final class
Java {

    private
    Java() {}
    
    /**
     * Constructs a reference exporter.
     * @param next  next exporter to try
     */
    static public Exporter
    bind(final Exporter next) {
        class ExporterX extends Struct implements Exporter, Serializable {
            static private final long serialVersionUID = 1L;

            public String
            run(final Object target) {
                final Class type= null!=target ? target.getClass() : Void.class;
                if (Class.class == type) { return name((Class)target); }
                if (Method.class == type) {
                    final Method m = (Method)target;
                    String p = property(m);
                    if (null == p) { p = m.getName(); }
                    return name(m.getDeclaringClass()) + "--" + p;
                }
                if (Field.class == type) {
                    final Field f = (Field)target;
                    return name(f.getDeclaringClass()) + "--" + f.getName();
                }
                if (Constructor.class == type) {
                    final Constructor c = (Constructor)target;
                    return name(c.getDeclaringClass()) + "--new";
                }
                return next.run(target);
            }
        }
        return new ExporterX();
    }
    
    /**
     * Constructs a reference importer.
     * @param base  base URL for the local namespace
     * @param code  class loader
     * @param next  next importer to try
     */
    static public Importer
    use(final String base, final ClassLoader code, final Importer next) {
        class ImporterX extends Struct implements Importer, Serializable {
            static private final long serialVersionUID = 1L;

            public Object
            run(final Class<?> type, final String URL) {
                if (base.equalsIgnoreCase(URI.resolve(URL, "."))) {
                    final String name = Path.name(URI.path(URL));
                    if (!"".equals(name)) {
                        final AnnotatedElement r = reflect(code, name);
                        if (null != r) { return r; }
                    }
                }
                return next.run(type, URL);
            }
        }
        return new ImporterX();
    }
    
    /**
     * Reflects a named code element.
     * @param code  class loader
     * @param name  code element name
     */
    static public AnnotatedElement
    reflect(final ClassLoader code, final String name) {
        final int dash = name.indexOf("--");
        final String typename = -1 == dash ? name : name.substring(0, dash);                        
        try {
            final Class declarer = load(code, typename);
            if (-1 == dash) { return declarer; }
            final String p = name.substring(dash + "--".length());
            if (p.equals("new")) {
                final PowerlessArray<Constructor> c =
                    Reflection.constructors(declarer);
                if (c.length() == 1) { return c.get(0); }
            }
            try {
                return Reflection.field(declarer, p);
            } catch (final NoSuchFieldException e) {}
            for (final Method m : Reflection.methods(declarer)) {
                String mn = property(m);
                if (null == mn) { mn = m.getName(); }
                if (mn.equals(p)) { return m; }
            }
        } catch (final ClassNotFoundException e) {}
        return null;
    }
    
    /**
     * Gets the corresponding property name.
     * <p>
     * This method implements the standard Java beans naming conventions.
     * </p>
     * @param method    candidate method
     * @return name, or null if the method is not a property accessor
     */
    static public String
    property(final Method method) {
        final String name = method.getName();
        String r =
            name.startsWith("get") &&
            name.length() != "get".length() &&
            Character.isUpperCase(name.charAt("get".length())) &&
            method.getParameterTypes().length == 0
                ? name.substring("get".length())
            : (name.startsWith("is") &&
               name.length() != "is".length() &&
               Character.isUpperCase(name.charAt("is".length())) &&
               method.getParameterTypes().length == 0
                ? name.substring("is".length())
            : null);
        if (null!=r && !(r.length()>1 && Character.isUpperCase(r.charAt(1)))) {
            r = Character.toLowerCase(r.charAt(0)) + r.substring(1);
        }
        return r;
    }

    /**
     * Finds a named instance member.
     * @param type  class to search
     * @param name  member name
     * @return corresponding member, or <code>null</code> if not found
     */
    static public Member
    dispatch(final Class<?> type, final String name) {
        for (final Method m : Reflection.methods(type)) {
            if (!isStatic(m.getModifiers())) {
                String mn = property(m);
                if (null == mn) {
                    mn = m.getName();
                }
                if (name.equals(mn)) {
                    final Method r = bubble(m.getName(), m.getParameterTypes(),
                                            m.getDeclaringClass(), m);
                    if (null != r) { return r; }
                }
            }
        }
        try {
            final Field f = Reflection.field(type, name);
            if (!isStatic(f.getModifiers()) &&
                isPublic(f.getDeclaringClass().getModifiers())) { return f; }
        } catch (final NoSuchFieldException e) {}
        return null;
    }
    
    /**
     * Is the given type a pass-by-construction type?
     * @param type  candidate type
     * @return <code>true</code> if pass-by-construction,
     *         else <code>false</code>
     */
    static public boolean
    isPBC(final Class<?> type) {
        return org.ref_send.Record.class.isAssignableFrom(type) ||
            Throwable.class.isAssignableFrom(type) ||
            org.joe_e.array.ConstArray.class.isAssignableFrom(type) ||
            java.lang.reflect.Type.class.isAssignableFrom(type) ||
            java.lang.reflect.AnnotatedElement.class.isAssignableFrom(type) ||
            String.class == type ||
            Void.class == type ||
            Integer.class == type ||
            Long.class == type ||
            java.math.BigInteger.class == type ||
            Byte.class == type ||
            Short.class == type ||
            Boolean.class == type ||
            Character.class == type ||
            Double.class == type ||
            Float.class == type ||
            java.math.BigDecimal.class == type;
    }

    /**
     * Find the super most safe declaration of the given method.
     */
    static private Method
    bubble(final String name, final Class[] param,
           final Class declarer, Method method) {
        if (!isPublic(declarer.getModifiers())) {
            method = null;
        }
        final Class parent = declarer.getSuperclass();
        if (null != parent) {
            try {
                final Method specification = bubble(name, param, parent,
                    Reflection.method(parent, name, param));
                if (null != specification) { return specification; }
            } catch (final NoSuchMethodException e) {}
        }
        for (final Class i : declarer.getInterfaces()) {
            try {
                final Method specification = bubble(name, param, i,
                    Reflection.method(i, name, param));
                if (null != specification) { return specification; }
            } catch (final NoSuchMethodException e) {}
        }
        return method;
    }
    
    static String
    name(final Class<?> type) {
        return RuntimeException.class == type
            ? "Error"
        : (Token.class == type
            ? "org.ref_send.Permission"
        : (ClassCastException.class == type
            ? "org.ref_send.Forgery"
        : (NullPointerException.class == type
            ? "org.ref_send.promise.Indeterminate"
        : type.getName().replace('$', '-'))));
    }
    
    static Class
    load(final ClassLoader code,
         final String name) throws ClassNotFoundException {
        return "Error".equals(name)
            ? RuntimeException.class
        : ("org.ref_send.Permission".equals(name)
            ? Token.class
        : ("org.ref_send.Forgery".equals(name)
            ? ClassCastException.class
        : ("org.ref_send.promise.Indeterminate".equals(name)
            ? NullPointerException.class
        : code.loadClass(name.replace('-', '$')))));
    }
}
