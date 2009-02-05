// Copyright 2007-2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import java.io.BufferedWriter;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.joe_e.Struct;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.charset.UTF8;
import org.joe_e.reflect.Reflection;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.promise.Fulfilled;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.Volatile;
import org.ref_send.scope.Scope;
import org.ref_send.type.Typedef;
import org.waterken.syntax.Exporter;
import org.waterken.syntax.Serializer;

/**
 * Serializes an array of Java objects to a JSON byte stream.
 */
public final class
JSONSerializer extends Struct implements Serializer, Record, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * Constructs an instance.
     */
    public @deserializer
    JSONSerializer() {}

    // org.waterken.syntax.Serializer interface

    public ByteArray
    run(final Exporter export, final ConstArray<?> values) throws Exception {
        /*
         * SECURITY CLAIM: Only the immutable root of the application object
         * tree provided by the values argument is serialized. The Exporter is
         * used to assign a URL to each mutable sub-tree. This constraint
         * ensures that application objects cannot cause repeated serialization
         * of an object tree to result in different JSON texts. If the behavior
         * of the provided Exporter is deterministic, always producing the same
         * URL for the same object, then repeated serialization of an object
         * tree produces identical JSON text.
         * 
         * SECURITY CLAIM: Iteration of the immutable root is done without
         * causing execution of application code. This constraint ensures that
         * serialization has no effect on the application object tree.
         * 
         * SECURITY DEPENDENCY: Application code cannot extend ConstArray, so
         * iteration of the values array will not transfer control to
         * application code.
         */
        final ByteArray.BuilderOutputStream buffer =
            ByteArray.builder(512).asOutputStream();
        write(export, values, new BufferedWriter(UTF8.output(buffer)));
        return buffer.snapshot();
    }
    
    /**
     * Serializes a stream of Java objects to a JSON text stream.
     * @param export    reference exporter
     * @param values    each value to serialize
     * @param text      UTF-8 text output, will be flushed and closed
     */
    static public void
    write(final Exporter export, final Iterable<?> values,
                                 final Writer text) throws Exception {
        final JSONWriter top = JSONWriter.make(text);
        final JSONWriter.ArrayWriter aout = top.startArray();
        for (final Object value : values) {
            serialize(export, Object.class, value, aout.startElement());
        }
        aout.finish();
        if (!top.isWritten()) { throw new RuntimeException(); }
        text.flush();
        text.close();
    }

    static private final TypeVariable<?> R = Typedef.var(Volatile.class, "T");
    static private final TypeVariable<?> T = Typedef.var(Iterable.class, "T");

    static private final Class<?> Inline = Fulfilled.ref(0).getClass();
    
    static private void
    serialize(final Exporter export, final Type implicit, final Object value,
              final JSONWriter.ValueWriter out) throws Exception {
        final Class<?> actual = null != value ? value.getClass() : Void.class;
        if (String.class == actual) {
            out.writeString((String)value);
        } else if (Integer.class == actual) {
            out.writeInt((Integer)value);
        } else if (Boolean.class == actual) {
            out.writeBoolean((Boolean)value);
        } else if (Long.class == actual) {
            try {
                out.writeLong((Long)value);
            } catch (final ArithmeticException e) {
                serialize(export, implicit, new Rejected<Long>(e), out);
            }
        } else if (Double.class == actual) {
            try {
                out.writeDouble((Double)value);
            } catch (final ArithmeticException e) {
                serialize(export, implicit, new Rejected<Double>(e), out);
            }
        } else if (Float.class == actual) {
            try {
                out.writeFloat((Float)value);
            } catch (final ArithmeticException e) {
                serialize(export, implicit, new Rejected<Float>(e), out);
            }
        } else if (Byte.class == actual) {
            out.writeInt((Byte)value);
        } else if (Short.class == actual) {
            out.writeInt((Short)value);
        } else if (Character.class == actual) {
            out.writeString(((Character)value).toString());
        } else if (Void.class == actual) {
            out.writeNull();
        } else if (Scope.class == actual) {
            final Scope scope = (Scope)value;
            /*
             * SECURITY DEPENDENCY: Application code cannot extend ConstArray,
             * so iteration of the scope arrays will not transfer control to
             * application code.
             */
            final JSONWriter.ObjectWriter oout = out.startObject();
            final int length = scope.values.length();
            for (int i = 0; i != length; ++i) {
                final Object member = scope.values.get(i);
                if (null != member) {
                    serialize(export, Object.class, member,
                              oout.startMember(scope.meta.names.get(i)));
                }
            }
            oout.finish();
        } else if (Inline == actual) {
            final Type r = Typedef.value(R, implicit);
            serialize(export, null != r ? r : Object.class,
                      ((Fulfilled<?>)value).cast(), out);
            /*
             * SECURITY DEPENDENCY: The Inline.cast() method simply returns the
             * value of the final field containing the promise's referent.
             */ 
        } else if (value instanceof ConstArray) {
            /*
             * SECURITY DEPENDENCY: Application code cannot extend ConstArray,
             * so iteration of the value array will not transfer control to
             * application code.
             */
            final Type elementType = Typedef.bound(T, implicit);
            final JSONWriter.ArrayWriter aout = out.startArray();
            for (final Object element : (ConstArray<?>)value) {
                serialize(export, elementType, element, aout.startElement());
            }
            aout.finish();
        } else if (value instanceof Record || value instanceof Throwable) {
            final JSONWriter.ObjectWriter oout = out.startObject();
            final Class<?> top = Typedef.raw(implicit);
            if (actual != top) {
                serialize(export, PowerlessArray.class,
                          upto(actual, top), oout.startMember("$"));
            }
            for (final Field f : Reflection.fields(actual)) {
                if (!Modifier.isStatic(f.getModifiers()) &&
                    Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                    if (!Modifier.isFinal(f.getModifiers())) {
                        throw new IllegalAccessException("MUST be final: " + f);
                    }
                    final Object member = Reflection.get(f, value);
                    if (null != member) {
                        serialize(export,
                                  Typedef.bound(f.getGenericType(), implicit),
                                  member, oout.startMember(f.getName()));
                    }
                }
            }
            oout.finish();
        } else {
            out.writeLink(export.run(value));
        }
    }

    /**
     * Enumerate an inheritance chain from [ bottom, top ).
     * @param bottom    bottom of the inheritance chain
     * @param top       top of the inheritance chain.
     */
    static private PowerlessArray<String>
    upto(final Class<?> bottom, final Class<?> top) {
        // simplify the knot at the top of the world
        final Class<?> limit = Struct.class.isAssignableFrom(bottom)
            ? Struct.class
        : RuntimeException.class.isAssignableFrom(bottom)
            ? (Exception.class.isAssignableFrom(top)
                ? RuntimeException.class
            : Exception.class)
        : Exception.class.isAssignableFrom(bottom)
            ? Throwable.class
        : Object.class;
        final PowerlessArray.Builder<String> r = PowerlessArray.builder(4);
        for (Class<?> i = bottom; top != i && limit != i; i=i.getSuperclass()) {
            if (Modifier.isPublic(i.getModifiers())) {
                try { r.append(Java.name(i)); } catch (final Exception e) {}
            }
        }
        return r.snapshot();
    }
}
