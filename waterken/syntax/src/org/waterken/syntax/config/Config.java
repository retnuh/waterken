// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.config;

import java.io.File;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ConstArray;
import org.joe_e.array.PowerlessArray;
import org.joe_e.file.Filesystem;
import org.waterken.syntax.Exporter;
import org.waterken.syntax.Importer;
import org.waterken.syntax.json.JSONDeserializer;
import org.waterken.syntax.json.JSONParser;
import org.waterken.syntax.json.JSONSerializer;

/**
 * A folder of serialized configuration settings.
 * <p>
 * This class provides convenient access to a folder of JSON files; each of
 * which represents a particular configuration setting. The class provides
 * methods for {@link #init initializing} and {@link read reading} these
 * settings.
 * </p>
 * <p>
 * For example, consider a folder with contents:
 * </p>
 * <pre>
 * config/
 *     - username.json
 *         [ "tyler.close" ]
 *     - port.json
 *         [ 8088 ]
 *     - home.json
 *         [ {
 *             "$" : [ "org.example.hypertext.Anchor" ],
 *             "icon" : "home.png",
 *             "href" : "http://waterken.sourceforge.net/",
 *             "tooltip" : "Home page"
 *           } ]
 * </pre>
 * <p>
 * These settings can be read with code:
 * </p>
 * <pre>
 * final Config config = &hellip;
 * final String username = config.read(String.class, "username");
 * final int port = config.read(int.class, "port");
 * final Anchor home = config.read(Anchor.class, "home");
 * </pre>
 */
public final class
Config extends Struct {

    private final File root;
    private final ClassLoader code;
    private final Importer connect;
    private final Exporter export;
    
    static private final class
    Cache {
        private PowerlessArray<String> keys;
        private ConstArray<Object> values;
        
        protected int
        find(final String key) {
            int i = keys.length();
            while (0 != i-- && !key.equals(keys.get(i))) {}
            return i;
        }
        
        protected Object
        at(final int i) { return values.get(i); }
        
        protected void
        put(final String key, final Object value) {
            keys = keys.with(key);
            values = values.with(value);
        }
    }
    
    private final Cache cache;
    
    /**
     * Constructs an instance.
     * @param root      root folder for configuration files
     * @param code      class loader for serialized objects
     * @param connect   reference importer, may be <code>null</code>
     * @param export    reference exporter, may be <code>null</code>
     */
    public
    Config(final File root, final ClassLoader code,
           final Importer connect, final Exporter export) {
        this.root = root;
        this.code = code;
        this.connect = connect;
        this.export = export;
        
        cache = new Cache();
    }

    static private final String ext = ".json";
    
    /**
     * Reads a configuration setting.
     * @param <T>   expected value type
     * @param type  expected value type
     * @param name  setting name
     * @return setting value, or <code>null</code> if not set
     */
    public @SuppressWarnings("unchecked") <T> T
    read(final Type type, final String name) {
        return (T)sub(root, "").run(type, name + ext, "file:///");
    }
    
    private Importer
    sub(final File root, final String prefix) {
        class ImporterX extends Struct implements Importer {
            public Object
            run(final Type type, final String href, final String base) {
                try {
                    if (!"file:///".equals(base) || -1 != href.indexOf(':')) {
                        return connect.run(type, href, base);
                    }

                    // descend to the named file
                    File folder = root;     // sub-folder containing file
                    String path = prefix;   // path to folder from config root
                    String name = href;     // filename
                    while (true) {
                        final int i = name.indexOf('/');
                        if (-1 == i) { break; }
                        folder = Filesystem.file(folder, name.substring(0, i));
                        path += name.substring(0, i + 1);
                        name = name.substring(i + 1);
                    }
                    if ("".equals(name)) { return folder; }
                    final File file = Filesystem.file(folder, name);
                    if (!name.endsWith(ext)) { return file; }
                    if (!file.isFile()) {return JSONParser.defaultValue(type);}
                    
                    // deserialize the named object
                    final String key = path + name;
                    final int i = cache.find(key);
                    if (-1 != i) { return cache.at(i); }
                    final Object r = new JSONDeserializer().run("file:///",
                        sub(folder, path), code, Filesystem.read(file),
                        ConstArray.array(type)).get(0);
                    cache.put(key, r);
                    return r;
                } catch (final Exception e) { throw new Error(e); }
            }
        }
        return new ImporterX();
    }
    
    /**
     * Initializes a configuration setting.
     * @param name      setting name
     * @param value     setting value
     */
    public void
    init(final String name, final Object value) {
        try {
            final String key = name + ext;
            new JSONSerializer().run(export, ConstArray.array(value),
                Filesystem.writeNew(Filesystem.file(root, key)));
            cache.put(key, value);
        } catch (final Exception e) { throw new Error(e); }
    }
    
    /**
     * Creates a temporary override of a configuration setting.
     * @param name      setting name
     * @param value     transient setting value
     */
    public void
    override(final String name, final Object value) {
        final String key = name + ext;
        Filesystem.file(root, key);
        cache.put(key, value);
    }
}
