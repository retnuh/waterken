// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ConstArray;
import org.joe_e.charset.UTF8;
import org.ref_send.Record;
import org.waterken.syntax.BadSyntax;
import org.waterken.syntax.Deserializer;
import org.waterken.syntax.Importer;

/**
 * Deserializes a JSON byte stream.
 */
public final class
JSONDeserializer extends Struct implements Deserializer, Record, Serializable {
    static private final long serialVersionUID = 1L;

    public Object
    deserialize(final InputStream content, final Importer connect,
            final String base, final ClassLoader code,
            final Type type) throws IOException, BadSyntax {
        return new JSONParser(base, connect, code,
            new BufferedReader(UTF8.input(content))).readValue(type);
    }
    
    public ConstArray<?>
    deserializeTuple(final InputStream content, final Importer connect,
                     final String base, final ClassLoader code,
                     final Type... types) throws Exception {
        return new JSONParser(base, connect, code,
                new BufferedReader(UTF8.input(content))).readTuple(types);
    }
}
