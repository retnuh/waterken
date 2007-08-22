// Copyright 2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.jos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import org.waterken.model.Heap;

/**
 * A persistent field.
 */
final class
FieldWrapper implements Wrapper {
    static private final long serialVersionUID = 1;

    private transient Field code;
    
    FieldWrapper(final Field code) {
        this.code = code;
    }
    
    // java.io.Serializable interface
    
    private void
    writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeObject(code.getDeclaringClass());
        out.writeUTF(code.getName());
    }

    private void
    readObject(final ObjectInputStream in) throws IOException,
                                                  ClassNotFoundException {
        in.defaultReadObject();

        final Class declarer = (Class)in.readObject();
        final String name = in.readUTF();
        try {
            code = declarer.getField(name);
        } catch (final NoSuchFieldException e) {
            throw new ClassNotFoundException(declarer.getName() + "#" + name);
        }
    }
    
    // org.waterken.jos.Wrapper interface
    
    public Object
    peel(final Heap loader) { return code; }
}
