// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import static org.joe_e.array.PowerlessArray.builder;

import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.joe_e.Struct;
import org.joe_e.array.ArrayBuilder;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.charset.UTF8;
import org.joe_e.reflect.Reflection;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.promise.Inline;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.Volatile;
import org.ref_send.type.Typedef;
import org.waterken.id.Exporter;
import org.waterken.io.Content;
import org.waterken.io.open.Open;
import org.waterken.syntax.Serializer;

/**
 * <a href="http://www.json.org/">JSON</a> serialization.
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

    public Content
    run(final boolean mode, final Exporter export, final ConstArray<?> object) {
        return new Content() {
            public void
            writeTo(final OutputStream out) throws Exception {
                final Writer text = UTF8.output(Open.output(out));
                final ValueWriter top = new ValueWriter("", text);
                serialize(mode, ConstArray.class, object, export, top);
                if (!top.isWritten()) { throw new NullPointerException(); }
                text.write(ValueWriter.newLine);
                text.flush();
                text.close();
            }
        };
    }

    static private final TypeVariable<?> R = Typedef.name(Volatile.class, "T");
    static private final TypeVariable<?> T = Typedef.name(Iterable.class, "T");

    static private void
    serialize(final boolean mode, final Type implicit, final Object object,
              final Exporter export, final ValueWriter out) throws Exception {
        final Class<?> actual = null != object ? object.getClass() : Void.class;
        if (Inline.class == actual) {
            final Type r = Typedef.value(R, implicit);
            serialize(mode, null != r ? r : Object.class,
                      ((Inline<?>)object).cast(), export, out);
        } else if (String.class == actual) {
            out.writeString((String)object);
        } else if (Void.class == actual) {
            out.writeNull();
        } else if (Integer.class == actual) {
            out.writeInt((Integer)object);
        } else if (Long.class == actual) {
            out.writeLong((Long)object);
        } else if (BigInteger.class == actual) {
            out.writeInteger((BigInteger)object);
        } else if (Byte.class == actual) {
            out.writeByte((Byte)object);
        } else if (Short.class == actual) {
            out.writeShort((Short)object);
        } else if (Boolean.class == actual) {
            out.writeBoolean((Boolean)object);
        } else if (Character.class == actual) {
            out.writeString(((Character)object).toString());
        } else if (Double.class == actual) {
            try {
                out.writeDouble((Double)object);
            } catch (final ArithmeticException e) {
                serialize(mode, implicit, new Rejected<Double>(e), export, out);
            }
        } else if (Float.class == actual) {
            try {
                out.writeFloat((Float)object);
            } catch (final ArithmeticException e) {
                serialize(mode, implicit, new Rejected<Float>(e), export, out);
            }
        } else if (BigDecimal.class == actual) {
            out.writeDecimal((BigDecimal)object);
        } else if (Class.class == actual) {
            final Class<?> c = (Class<?>)object;
            final ValueWriter.ObjectWriter oout = out.startObject();
            if (Class.class != implicit) {
                serialize(mode, PowerlessArray.class,
                          PowerlessArray.array("class"), export,
                          oout.startMember("$"));
            }
            oout.startMember("name").writeString(Java.name(c));
            oout.close();
        } else if (object instanceof ConstArray) {
            final Type valueType = Typedef.bound(T, implicit);
            final ValueWriter.ArrayWriter aout = out.startArray();
            for (final Object value : (ConstArray<?>)object) {
                serialize(mode, valueType, value, export, aout.startElement());
            }
            aout.close();
        } else if (object instanceof Record || object instanceof Throwable) {
            final ValueWriter.ObjectWriter oout = out.startObject();
            final Class<?> top = Typedef.raw(implicit);
            if (actual != top) {
                serialize(render, PowerlessArray.class, upto(actual, top),
                          export, oout.startMember("$"));
            }
            for (final Field f : Reflection.fields(actual)) {
                final int flags = f.getModifiers();
                if (!Modifier.isStatic(flags) && Modifier.isFinal(flags) &&
                    Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                    final Object value = Reflection.get(f, object);
                    if (null != value) {
                        serialize(render,
                                  Typedef.bound(f.getGenericType(), actual),
                                  value, export, oout.startMember(f.getName()));
                    }
                }
            }
            oout.close();
        } else if (render == mode || Java.isPBC(actual)) {
            out.writeLink(export.run(object));
        // rest is introspection support not used in normal messaging
        } else {
            final ValueWriter.ObjectWriter oout = out.startObject();
            final Class<?> end =
                Struct.class.isAssignableFrom(actual)?Struct.class:Object.class;
            final PowerlessArray.Builder<String> r = builder(4);
            for (Class<?> i=actual; end!=i; i=i.getSuperclass()) { all(i, r); }
            serialize(mode, PowerlessArray.class, r.snapshot(), export,
                      oout.startMember("$"));
            for (final Method m : Reflection.methods(actual)) {
                final int flags = m.getModifiers();
                if (!Modifier.isStatic(flags) && !Java.isSynthetic(flags)) {
                    final String name = Java.property(m);
                    final ValueWriter.ObjectWriter mout = oout.startMember(
                        null != name ? name : m.getName()).startObject();

                    // output the return type
                    final Type outType = m.getGenericReturnType();
                    if (void.class != outType && Void.class != outType) {
                        describeType(outType, mout.startMember("out"));
                    }

                    // output the parameter types
                    if (null == name) {
                        final ValueWriter.ArrayWriter pout =
                            mout.startMember("in").startArray();
                        for (final Type p : m.getGenericParameterTypes()) {
                            describeType(p, pout.startElement());
                        }
                        pout.close();
                    }

                    // output the error types
                    final ValueWriter.ArrayWriter eout =
                        mout.startMember("error").startArray();
                    for (final Type e : m.getGenericExceptionTypes()) {
                        describeType(e, eout.startElement());
                    }
                    eout.close();

                    mout.close();
                }
            }
            oout.close();
        }
    }

    static private void
    describeType(final Type type, final ValueWriter out) throws Exception {
        final Type pR = Typedef.value(R, type);
        if (null != pR) {
            describeType(pR, out);
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType generic = (ParameterizedType)type;
            final ValueWriter.ObjectWriter oout = out.startObject();
            final Class<?> raw = (Class<?>)generic.getRawType();
            oout.startMember("name").writeString(Java.name(jsonType(raw)));
            final ValueWriter.ArrayWriter pout =
                oout.startMember("arguments").startArray();
            for (final Type argument : generic.getActualTypeArguments()) {
                describeType(argument, pout.startElement());
            }
            pout.close();
            oout.close();
        } else {
            final Class<?> c = Typedef.raw(type);
            final ValueWriter.ObjectWriter oout = out.startObject();
            oout.startMember("name").writeString(Java.name(jsonType(c)));
            oout.close();
        }
    }

    static private Class<?>
    jsonType(final Class<?> r) {
        return Boolean.class == r
            ? boolean.class
        : byte.class == r || Byte.class == r ||
          short.class == r || Short.class == r ||
          int.class == r || Integer.class == r ||
          long.class == r || Long.class == r ||
          java.math.BigDecimal.class == r ||
          float.class == r || Float.class == r ||
          double.class == r || Double.class == r ||
          java.math.BigInteger.class == r
            ? Number.class
        : char.class == r || Character.class == r
            ? String.class
        : Class.class == r
            ? Type.class
        : Field.class == r || Member.class == r || Constructor.class == r
            ? Method.class
        : Exception.class == r
            ? RuntimeException.class
        : ConstArray.class.isAssignableFrom(r)
            ? ConstArray.class
        : r;
    }

    static private PowerlessArray<String>
    upto(final Class<?> bottom, final Class<?> top) {
        final Class<?> limit = Struct.class.isAssignableFrom(bottom)
            ? Struct.class
        : RuntimeException.class.isAssignableFrom(bottom)
            ? Exception.class
        : Exception.class.isAssignableFrom(bottom)
            ? Throwable.class
        : Object.class;
        final PowerlessArray.Builder<String> r = builder(4);
        for (Class<?> i = bottom; top != i && limit != i; i=i.getSuperclass()) {
            if (Modifier.isPublic(i.getModifiers())) {
                try { r.append(Java.name(i)); } catch (final Exception e) {}
            }
        }
        return r.snapshot();
    }


    static private void
    all(final Class<?> type, final ArrayBuilder<String> r) {
        if (type == Serializable.class) { return; }
        if (Modifier.isPublic(type.getModifiers())) {
            try { r.append(Java.name(type)); } catch (final Exception e) {}
        }
        for (final Class<?> i : type.getInterfaces()) { all(i, r); }
    }
}
