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
import java.math.BigDecimal;
import java.math.BigInteger;

import org.joe_e.Struct;
import org.joe_e.inert;
import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.charset.UTF8;
import org.joe_e.reflect.Reflection;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.promise.Promise;
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
    serialize(final Exporter export,
              final Type type, final @inert Object value) throws Exception {
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
        write(export, type, value, new BufferedWriter(UTF8.output(buffer)));
        return buffer.snapshot();
    }
    
    public ByteArray
    serializeTuple(final Exporter export, final ConstArray<Type> types,
                   final @inert ConstArray<?> values) throws Exception {
        final ByteArray.BuilderOutputStream buffer =
            ByteArray.builder(512).asOutputStream();
        final Writer text = new BufferedWriter(UTF8.output(buffer));
        final JSONWriter top = JSONWriter.make(text);
        final JSONWriter.ArrayWriter aout = top.startArray();
        for (int i = 0; i != values.length(); ++i) {
            serialize(export, types.get(i), values.get(i), aout.startElement());
        }
        aout.finish();
        if (!top.isWritten()) { throw new RuntimeException(); }
        text.flush();
        text.close();
        return buffer.snapshot();
    }
    
    /**
     * Serializes a stream of Java objects to a JSON text stream.
     * @param export    reference exporter
     * @param type      implicit type for <code>value</code>
     * @param value     value to serialize
     * @param text      UTF-8 text output, will be flushed and closed
     */
    static public void
    write(final Exporter export, final Type type, final @inert Object value,
                                 final Writer text) throws Exception {
        final JSONWriter top = JSONWriter.make(text);
        serialize(export, type, value, top);
        if (!top.isWritten()) { throw new RuntimeException(); }
        text.flush();
        text.close();
    }

    static private final TypeVariable<?> R = Typedef.var(Promise.class, "T");
    static private final TypeVariable<?> T = Typedef.var(Iterable.class, "T");
    
    static private void
    serialize(final Exporter export, final Type implicit,
              final @inert Object value, final JSONWriter out) throws Exception{
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
                serialize(export, implicit, JSON.Rejected.make(e), out);
            }
        } else if (Double.class == actual) {
            try {
                out.writeDouble((Double)value);
            } catch (final ArithmeticException e) {
                serialize(export, implicit, JSON.Rejected.make(e), out);
            }
        } else if (Float.class == actual) {
            try {
                out.writeFloat((Float)value);
            } catch (final ArithmeticException e) {
                serialize(export, implicit, JSON.Rejected.make(e), out);
            }
        } else if (Byte.class == actual) {
            out.writeInt((Byte)value);
        } else if (Short.class == actual) {
            out.writeInt((Short)value);
        } else if (Character.class == actual) {
            out.writeString(((Character)value).toString());
        } else if (Void.class == actual) {
            out.writeNull();
        } else if (BigInteger.class == actual) {
            final BigInteger num = (BigInteger)value;
            if (num.bitLength() < Integer.SIZE) {
                serialize(export, implicit, num.intValue(), out);
            } else if (num.bitLength() < Long.SIZE) {
                serialize(export, implicit, num.longValue(), out);
            } else {
                serialize(export, implicit,
                          JSON.Rejected.make(new ArithmeticException()), out);
            }
        } else if (BigDecimal.class == actual) {
            serialize(export, implicit, ((BigDecimal)value).doubleValue(), out);
        } else if (value instanceof ConstArray) {
            /*
             * SECURITY DEPENDENCY: Application code cannot extend ConstArray,
             * so iteration of the value array will not transfer control to
             * application code.
             */
            final Type elementType = Typedef.bound(T, implicit);
            final JSONWriter.ArrayWriter aout = out.startArray();
            for (final @inert Object element : (ConstArray<?>)value) {
                serialize(export, elementType, element, aout.startElement());
            }
            aout.finish();
        } else if (Scope.class == actual) {
            final @inert Scope scope = (Scope)value;
            /*
             * SECURITY DEPENDENCY: Application code cannot extend ConstArray,
             * so iteration of the scope arrays will not transfer control to
             * application code.
             */
            final JSONWriter.ObjectWriter oout = out.startObject();
            final int length = scope.values.length();
            for (int i = 0; i != length; ++i) {
                final @inert Object member = scope.values.get(i);
                if (null != member) {
                    serialize(export, Object.class, member,
                              oout.startMember(scope.meta.names.get(i)));
                }
            }
            oout.finish();
        } else if (value instanceof Record || value instanceof Throwable) {
            final JSONWriter.ObjectWriter oout = out.startObject();
            final Type promised = Typedef.value(R, implicit);
            final Type expected = null != promised ? promised : implicit;
            final PowerlessArray<String> types =
                JSON.upto(actual, Typedef.raw(expected));
            if (0 != types.length()) {
                serialize(export, PowerlessArray.class,
                          types, oout.startMember("$"));
            }
            for (final Field f : Reflection.fields(actual)) {
                if (!Modifier.isStatic(f.getModifiers()) &&
                    Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                    if (!Modifier.isFinal(f.getModifiers())) {
                        throw new IllegalAccessException("MUST be final: " + f);
                    }
                    final @inert Object member = Reflection.get(f, value);
                    if (null != member) {
                        serialize(export,
                                  Typedef.bound(f.getGenericType(), implicit),
                                  member, oout.startMember(f.getName()));
                    }
                }
            }
            oout.finish();
        } else {
            final Type promised = Typedef.value(R, implicit);
            final Type expected = null != promised ? promised : implicit;
            final PowerlessArray<String> types =
                JSON.upto(actual, Typedef.raw(expected));
            if (0 == types.length()) {
                out.writeLink(export.run(value));
            } else {
                final JSONWriter.ObjectWriter oout = out.startObject();
                serialize(export, PowerlessArray.class,
                          types, oout.startMember("$"));
                oout.startMember("@").writeString(export.run(value));
                oout.finish();
            }
        }
    }
}
