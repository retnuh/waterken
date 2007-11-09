// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

import org.joe_e.Equatable;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.reflect.Reflection;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.ref_send.promise.NegativeInfinity;
import org.ref_send.promise.PositiveInfinity;
import org.ref_send.promise.Rejected;
import org.ref_send.promise.Volatile;
import org.ref_send.promise.eventual.Do;
import org.ref_send.promise.eventual.Eventual;
import org.ref_send.type.Typedef;
import org.waterken.id.Importer;
import org.waterken.uri.URI;

final class
JSONParser {
    
    static private final String whitespace = " \n\r\t";
    
    static private interface
    State extends Equatable {
        void
        run(char c) throws Exception;
    }

    private final String base;
    private final Importer connect;
    private final ClassLoader code;
    private final ArrayList<State> state;
    
    JSONParser(final String base,
               final Importer connect,
               final ClassLoader code) {
        this.base = base;
        this.connect = connect;
        this.code = code;
        state = new ArrayList<State>();
    }
    
    private State
    current() { return state.get(state.size() - 1); }
    
    private State
    pop() { return state.remove(state.size() - 1); }
    
    private void
    push(final State child) { state.add(child); }
    
    private void
    to(final State next) { state.set(state.size() - 1, next); }
    
    ConstArray<?>
    parse(final Reader in,
          final PowerlessArray<Type> parameters) throws Exception {
        final State done = new State() {
            public void
            run(final char c) throws Exception {
                if (whitespace.indexOf(c) == -1) {
                    throw new Exception("" + c);
                }
            }
        };
        push(done);
        final ConstArray[] root = { null };
        push(parseStart(parameters, new Do<ConstArray<?>,Void>() {
            public Void
            fulfill(final ConstArray<?> referent) {
                root[0] = referent;
                return null;
            }
        }));
        int line = 1;
        int column = 1;
        try {
            for (int i = in.read(); -1 != i; i = in.read()) {
                current().run((char)i);
                if ('\n' == i) {
                    ++line;
                    column = 1;
                } else {
                    ++column;
                }
            }
            if (done != pop()) { throw new Exception(); }
        } catch (final Exception e) {
            try { in.close(); } catch (final Exception e2) {}
            throw new Exception("( " + line + ", " + column + " ) : ", e);
        }
        in.close();
        return root[0];
    }
    
    private State
    parseStart(final PowerlessArray<Type> parameters,
               final Do<ConstArray<?>,?> out) {
        return new State() {
            public void
            run(final char c) throws Exception {
                switch (c) {
                case '[':
                    to(parseTuple(parameters, out));
                    break;
                default:
                    if (whitespace.indexOf(c) == -1) {
                        throw new Exception("" + c);
                    }
                }
            }
        };
    }

    /**
     * {@link Volatile} expected type
     */
    static private final TypeVariable R = Typedef.name(Volatile.class, "T");
    
    private State
    parseTuple(final PowerlessArray<Type> parameters,
               final Do<ConstArray<?>,?> out) {
        final Object[] arg = new Object[parameters.length()];
        return new State() {
            private int i = 0;
            
            public void
            run(final char c) throws Exception {
                if (']' == c) {
                    pop();
                    out.fulfill(ConstArray.array(arg));
                } else if (whitespace.indexOf(c) != -1) {
                    // ignore whitespace
                } else {
                    push(parseContinuation(']'));
                    final Type p = parameters.get(i);
                    final Type r = Typedef.value(R, p);
                    push(parseValue(null != r ? r : p, new Do<Object,Void>() {
                        public Void
                        fulfill(final Object x) {
                            arg[i++] = null != r ? Eventual.promised(x) : x;
                            return null;
                        }
                    }));
                    current().run(c);
                }
            }
        };
    }
    
    private State
    parseValue(final Type implicit, final Do<Object,?> out) {
        return new State() {
            public void
            run(final char c) throws Exception {
                switch (c) {
                case '\"':
                    to(parseString(implicit, out));
                    break;
                case '{':
                    to(parseObject(implicit, out));
                    break;
                case '[':
                    to(parseArray(implicit, out));
                    break;
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    to(parseNumber(implicit, out));
                    current().run(c);
                    break;
                case 't':
                    to(parseToken("true", Boolean.TRUE, out));
                    break;
                case 'f':
                    to(parseToken("false", Boolean.FALSE, out));
                    break;
                case 'n':
                    to(parseToken("null", null, out));
                    break;
                default:
                    if (whitespace.indexOf(c) == -1) {
                        throw new Exception("" + c);
                    }
                }
            }
        };
    }
    
    private State
    parseObject(final Type implicit, final Do<Object,?> out) {
        return new State() {
            private Class actual;
            private Constructor make;
            private Type[] paramv;
            private String[] namev;
            private Object[] argv;
            
            /**
             * Determine the deserialization constructor.
             */
            private void
            determine() throws NoSuchMethodException {
                final Type declared = null == actual ? implicit : actual;
                
                if (null == actual) {
                    actual = Typedef.raw(implicit);
                }
                for (final Constructor i : Reflection.constructors(actual)) {
                    if (i.isAnnotationPresent(deserializer.class)) {
                        make = i;
                        break;
                    }
                }
                if (null == make) {
                    make = Reflection.constructor(actual);
                }
                paramv = make.getGenericParameterTypes();
                int i = 0;
                for (Type p : paramv) {
                    paramv[i++] = Typedef.bound(p, declared);
                }
                namev = new String[paramv.length];
                i = 0;
                for (final Annotation[] as : make.getParameterAnnotations()) {
                    for (final Annotation a : as) {
                        if (a instanceof name) {
                            namev[i] = ((name)a).value();
                            break;
                        }
                    }
                    ++i;
                }
                argv = new Object[paramv.length];
            }
            
            public void
            run(final char c) throws Exception {
                if ('}' == c) {
                    pop();
                    if (null == actual) { determine(); }
                    final Object r = Reflection.construct(make, argv);
                    if (r instanceof Rejected) {
                        final Rejected p = (Rejected)r;
                        if (Double.class==implicit || double.class==implicit) {
                            if (p.reason instanceof NegativeInfinity) {
                                out.fulfill(Double.NEGATIVE_INFINITY);
                            } else if (p.reason instanceof PositiveInfinity) {
                                out.fulfill(Double.POSITIVE_INFINITY);
                            } else {
                                out.fulfill(Double.NaN);
                            }
                        } else if(Float.class==implicit||float.class==implicit){
                            if (p.reason instanceof NegativeInfinity) {
                                out.fulfill(Float.NEGATIVE_INFINITY);
                            } else if (p.reason instanceof PositiveInfinity) {
                                out.fulfill(Float.POSITIVE_INFINITY);
                            } else {
                                out.fulfill(Float.NaN);
                            }
                        } else {
                            final Class<?> R = Typedef.raw(implicit);
                            if (!R.isInstance(r) && R.isInterface()) {
                                out.fulfill(p._(R));
                            } else {
                                out.fulfill(r);
                            }
                        }
                    } else {
                        out.fulfill(r);
                    }
                } else if ('\"' == c) {
                    push(parseString(String.class, new Do<Object,Void>() {
                        public Void
                        fulfill(final Object name) throws Exception {
                            if ("$".equals(name)) {
                                if (null != actual) { throw new Exception(); }
                                push(parseContinuation('}'));
                                push(parsePairValue(ConstArray.class, 
                                                    new Do<Object,Void>() {
                                    public Void
                                    fulfill(final Object x) throws Exception {
                                        for (final Object i : (ConstArray)x) {
                                            try {
                                                actual = Java.load(code,
                                                                   (String)i);
                                                break;
                                            } catch(ClassNotFoundException e) {}
                                        }
                                        determine();
                                        return null;
                                    }
                                }));
                            } else if ("@".equals(name)) {
                                if (null != actual) { throw new Exception(); }
                                actual = Typedef.raw(implicit);
                                pop();
                                push(new State() {
                                    public void
                                    run(final char c) throws Exception {
                                        if ('}' == c) {
                                            pop();
                                        } else {
                                            if (whitespace.indexOf(c) == -1) {
                                                throw new Exception("" + c);
                                            }
                                        }
                                    }
                                });
                                push(parsePairValue(implicit, 
                                                    new Do<Object,Void>() {
                                    public Void
                                    fulfill(final Object x) throws Exception {
                                        final String id = (String)x;
                                        out.fulfill(connect.run(actual,
                                               URI.resolve(base, id)));
                                        return null;
                                    }
                                }));
                            } else {
                                if (null == actual) { determine(); }
                                int i = namev.length;
                                while (0 != i-- && !name.equals(namev[i])) {}
                                final int position = i;
                                final Type p = -1!=i ? paramv[i] : Object.class;
                                final Type r = Typedef.value(R, p);
                                push(parseContinuation('}'));
                                push(parsePairValue(null != r ? r : p,
                                                    new Do<Object,Void>() {
                                    public Void
                                    fulfill(final Object x) throws Exception {
                                        if (-1 != position) {
                                            argv[position] = null != r
                                                ? Eventual.promised(x) : x;
                                        }
                                        return null;
                                    }
                                }));
                            }
                            return null;
                        }
                    }));
                } else {
                    if (whitespace.indexOf(c) == -1) {
                        throw new Exception("" + c);
                    }
                }
            }
        };
    }
    
    private State
    parseContinuation(final char bracket) {
        return new State() {
            public void
            run(final char c) throws Exception {
                if (',' == c) {
                    pop();
                } else if (bracket == c) {
                    pop();
                    current().run(c);
                } else {
                    if (whitespace.indexOf(c) == -1) {
                        throw new Exception("" + c);
                    }
                }
            }
        };
    }
    
    private State
    parsePairValue(final Type implicit, final Do<Object,?> out) {
        return new State() {
            public void
            run(final char c) throws Exception {
                if (':' == c) {
                    to(parseValue(implicit, out));
                } else {
                    if (whitespace.indexOf(c) == -1) {
                        throw new Exception("" + c);
                    }
                }
            }
        };
    }

    static private final TypeVariable T = Typedef.name(Iterable.class, "T");
    
    private State
    parseArray(final Type implicit, final Do<Object,?> out) {
        final Type valueType = Typedef.bound(T, implicit);
        final Type r = Typedef.value(R, valueType);
        final ArrayList<Object> values = new ArrayList<Object>(); 
        return new State() {
            public void
            run(final char c) throws Exception {
                if (']' == c) {
                    pop();

                    // find the constructor
                    final Class actual = Typedef.raw(implicit);
                    Method make = null;
                    for (final Method m : Reflection.methods(actual)) {
                        if ("array".equals(m.getName())) {
                            make = m;
                            break;
                        }
                    }
                    if (null == make) {
                        throw new NoSuchMethodException(
                                actual.getName() + ".array");
                    }

                    // determine the array element type: use the type declared
                    // by the array constructor, since it can use primitive
                    // types, whereas the template parameters cannot.
                    Class<?> t = make.getParameterTypes()[0].getComponentType();
                    if (Object.class == t) {
                        t = Typedef.raw(valueType);
                    }

                    // fill out an array
                    final Object v = Array.newInstance(t, values.size());
                    int i = 0;
                    for (final Object x : values) {
                        Array.set(v, i++, x);
                    }
                    out.fulfill(Reflection.invoke(make, null, v));
                } else if (whitespace.indexOf(c) != -1) {
                    // ignore whitespace
                } else {
                    push(parseContinuation(']'));
                    push(parseValue(null!=r?r:valueType, new Do<Object,Void>() {
                        public Void
                        fulfill(final Object x) {
                            values.add(null != r ? Eventual.promised(x) : x);
                            return null;
                        }
                    }));
                    current().run(c);
                }
            }
        };
    }
    
    private State
    parseNumber(final Type implicit, final Do<Object,?> out) {
        final StringBuilder buffer = new StringBuilder();
        return new State() {
            public void
            run(final char c) throws Exception {
                if ('-' == c) {
                    if (buffer.length() != 0) { throw new Exception(); }
                    buffer.append(c);
                } else if ('0' <= c && '9' >= c) {
                    buffer.append(c);
                } else if ('.' == c || 'e' == c || 'E' == c) {
                    to(parseDecimal(implicit, buffer, out));
                    buffer.append(c);
                } else {
                    pop();
                    final String text = buffer.toString();
                    if (int.class == implicit || Integer.class == implicit) {
                        out.fulfill(Integer.parseInt(text));
                    } else if (long.class==implicit || Long.class == implicit) {
                        out.fulfill(Long.parseLong(text));
                    } else if (byte.class==implicit || Byte.class == implicit) {
                        out.fulfill(Byte.parseByte(text));
                    } else if (short.class==implicit || Short.class==implicit) {
                        out.fulfill(Short.parseShort(text));
                    } else if (BigInteger.class == implicit) {
                        out.fulfill(new BigInteger(text));
                    } else if (double.class==implicit||Double.class==implicit) {
                        out.fulfill(Double.valueOf(text));
                    } else if (float.class==implicit || Float.class==implicit) {
                        out.fulfill(Float.valueOf(text));
                    } else if (BigDecimal.class == implicit) {
                        out.fulfill(new BigDecimal(text));
                    } else {
                        final BigInteger x = new BigInteger(text);
                        int bits = x.bitLength();
                        if (x.signum() > 0) { ++bits; }
                        if (bits <= Byte.SIZE) {
                            out.fulfill(x.byteValue());
                        } else if (bits <= Short.SIZE) {
                            out.fulfill(x.shortValue());
                        } else if (bits <= Integer.SIZE) {
                            out.fulfill(x.intValue());
                        } else if (bits <= Long.SIZE) {
                            out.fulfill(x.longValue());
                        } else {
                            out.fulfill(x);
                        }
                    }
                    current().run(c);
                }
            }
        };
    }
    
    private State
    parseDecimal(final Type implicit, final StringBuilder buffer,
                 final Do<Object,?> out) {
        return new State() {
            public void
            run(final char c) throws Exception {
                if (('0' <= c && '9' >= c) || "eE+-".indexOf(c) != -1) {
                    buffer.append(c);
                } else {
                    pop();
                    final String text = buffer.toString();
                    if (double.class == implicit || Double.class == implicit) {
                        out.fulfill(Double.valueOf(text));
                    } else if (float.class==implicit || Float.class==implicit) {
                        out.fulfill(Float.valueOf(text));
                    } else {
                        out.fulfill(new BigDecimal(text));
                    }
                    current().run(c);
                }
            }
        };
    }
    
    private <T> State
    parseToken(final String name, final T value, final Do<T,?> out) {
        return new State() {
            private int i = 1;
            
            public void
            run(final char c) throws Exception {
                if (name.charAt(i) != c) { throw new Exception(); }
                if (++i == name.length()) {
                    pop();
                    out.fulfill(value);
                }
            }
        };
    }
    
    private State
    parseString(final Type implicit, final Do<Object,?> out) {
        final StringBuilder buffer = new StringBuilder();
        return new State() {
            public void
            run(final char c) throws Exception {
                if ('\"' == c) {
                    pop();
                    if (char.class == implicit || Character.class == implicit) {
                        if (1 != buffer.length()) { throw new Exception(); }
                        out.fulfill(buffer.charAt(0));
                    } else {
                        out.fulfill(buffer.toString());
                    }
                } else if ('\\' == c) {
                    push(parseEscape(buffer));
                } else {
                    buffer.append(c);
                }
            }
        };
    }
    
    private State
    parseEscape(final StringBuilder out) {
        return new State() {
            public void
            run(final char c) throws Exception {
                switch (c) {
                case '\"':
                    pop();
                    out.append('\"');
                    break;
                case '\\':
                    pop();
                    out.append('\\');
                    break;
                case '/':
                    pop();
                    out.append('/');
                    break;
                case 'b':
                    pop();
                    out.append('\b');
                    break;
                case 'f':
                    pop();
                    out.append('\f');
                    break;
                case 'n':
                    pop();
                    out.append('\n');
                    break;
                case 'r':
                    pop();
                    out.append('\r');
                    break;
                case 't':
                    pop();
                    out.append('\t');
                    break;
                case 'u':
                    to(parseUnicode(out));
                    break;
                default:
                    throw new Exception("" + c);
                }
            }            
        };
    }
    
    private State
    parseUnicode(final StringBuilder out) {
        return new State() {
            private int u = 0;
            private int i = 4;
            
            public void
            run(final char c) throws Exception {
                u <<= 4;
                if (0 >= c && 9 >= c) {
                    u |= (c - '0') & 0x0F;
                } else if ('A' <= c && 'F' >= c) {
                    u |= (c - 'A' + 10) & 0x0F;
                } else if ('a' <= c && 'f' >= c) {
                    u |= (c - 'a' + 10) & 0x0F;
                } else {
                    throw new Exception("" + c);
                }
                if (--i == 0) {
                    pop();
                    out.append((char)u);
                }
            }
        };
    }
}
