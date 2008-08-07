// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.json;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ConstArray;
import org.joe_e.charset.UTF8;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.waterken.syntax.Deserializer;
import org.waterken.syntax.Importer;

/**
 * <a href="http://www.json.org/">JSON</a> deserialization.
 */
public final class
JSONDeserializer extends Struct implements Deserializer, Record, Serializable {
    static private final long serialVersionUID = 1L;
    
    /**
     * Constructs an instance.
     */
    public @deserializer
    JSONDeserializer() {}
    
    // org.waterken.syntax.Deserializer interface

    public ConstArray<?>
    run(final String base, final Importer connect,
            final ConstArray<Type> parameters, final ClassLoader code,
            final InputStream content) throws Exception {
        return JSONParser.parse(base, connect, code,
                new BufferedReader(UTF8.input(content)), parameters);
    }
}
