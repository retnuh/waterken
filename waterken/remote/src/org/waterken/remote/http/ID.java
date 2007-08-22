// Copyright 2006-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import java.io.Serializable;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

import org.joe_e.Selfless;
import org.joe_e.Struct;
import org.ref_send.Brand;
import org.waterken.id.Exporter;
import org.waterken.id.Importer;
import org.waterken.uri.Path;
import org.waterken.uri.URI;

/**
 * An identifier.
 */
final class
ID<T> implements Brand<T>, Member, Selfless, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * string value of identifier
     */
    private final String name;

    /**
     * Constructs an instance.
     * @param name  {@link #name}
     */
    private
    ID(final String name) {
        if (null == name) { throw new NullPointerException(); }
        this.name = name;
    }

    // java.lang.Object interface

    public boolean
    equals(final Object o){return o instanceof ID && name.equals(((ID)o).name);}

    public int
    hashCode() { return 0x1D271F14; }

    // java.lang.reflect.Member interface
    
    public Class<?>
    getDeclaringClass() { return null; }
    
    public String
    getName() { return null; }
    
    public int
    getModifiers() { return Modifier.PUBLIC; }
    
    public boolean
    isSynthetic() { return true; }
    
    // org.waterken.remote.http.ID interface
    
    static Importer
    use(final String base, final Importer next) {
        class ImporterX extends Struct implements Importer, Serializable {
            static private final long serialVersionUID = 1L;

            public Object
            run(final Class<?> type, final String URL) {
                if (base.equalsIgnoreCase(URI.resolve(URL, "."))) {
                    return new ID(Path.name(URI.path(URL)));
                }
                return next.run(type, URL);
            }
        }
        return new ImporterX();
    }
    
    static Exporter
    bind(final Exporter next) {
        class ExporterX extends Struct implements Exporter, Serializable {
            static private final long serialVersionUID = 1L;

            public String
            run(final Object target) {
                if (target instanceof ID) { return ((ID)target).name; }
                return next.run(target);
            }
        }
        return new ExporterX();
    }
}
