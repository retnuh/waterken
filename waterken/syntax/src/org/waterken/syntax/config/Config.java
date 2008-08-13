// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.syntax.config;

import static org.ref_send.scope.Scope.Empty;

import java.io.File;
import java.lang.reflect.Type;

import org.joe_e.Struct;
import org.joe_e.array.ConstArray;
import org.joe_e.file.Filesystem;
import org.ref_send.scope.Scope;
import org.waterken.syntax.Exporter;
import org.waterken.syntax.Importer;
import org.waterken.syntax.json.JSONDeserializer;
import org.waterken.syntax.json.JSONSerializer;

/**
 * A folder of serialized configuration settings.
 * <p>
 * This class provides convenient access to a folder of JSON files; each of
 * which represents a particular configuration setting. The class provides
 * methods for {@link #init initializing} and {@link #read reading} these
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
 * final String username = config.read("username");
 * final int port = config.read("port");
 * final Anchor home = config.read("home");
 * </pre>
 */
public class
Config {

    /**
     * root folder for configuration files
     */
    public  final File root;
    
    /**
     * class loader for serialized objects
     */
    private final ClassLoader code;
    
    /**
     * reference importer, may be <code>null</code>
     */
    public  final Importer connect;
    
    /**
     * reference exporter, may be <code>null</code>
     */
    public  final Exporter export;
    
    private       Scope cache;
    
    /**
     * Constructs an instance.
     * @param root      {@link #root}
     * @param code      {@link #code}
     * @param connect   {@link #connect}
     * @param export    {@link #export}
     */
    public
    Config(final File root, final ClassLoader code,
           final Importer connect, final Exporter export) {
        this.root = root;
        this.code = code;
        this.connect = connect;
        this.export = export;
        
        cache = Empty.make();
    }
    
    /**
     * Reads a configuration setting.
     * <p>
     * This method is just syntactic sugar for:
     * </p>
     * <p>
     * <code>return {@link #readType readType}(name, Object.class);</code>
     * </p>
     * @param <T>   expected value type
     * @param name  setting name
     * @return setting value, or <code>null</code> if not set
     * @throws Exception    any problem connecting to the identified reference
     */
    public <T> T
    read(final String name) throws Exception {
        return readType(name, Object.class);
    }
    
    /**
     * Reads a configuration setting.
     * @param <T>   expected value type
     * @param name  setting name
     * @param type  expected value type
     * @return setting value, or <code>null</code> if not set
     * @throws Exception    any problem connecting to the identified reference
     */
    public @SuppressWarnings("unchecked") <T> T
    readType(final String name, final Type type) throws Exception {
        return (T)sub(root, "").run(name, "file:///", type);
    }

    static private final String ext = ".json";
    
    private Importer
    sub(final File root, final String prefix) {
        class ImporterX extends Struct implements Importer {
            public Object
            run(final String href, final String base,
                                   final Type type) throws Exception {
                if (!"file:///".equals(base) || -1 != href.indexOf(':')) {
                    return connect.run(href, base, type);
                }
                
                // check the cache
                final String key = prefix + href; {
                    final int i = cache.meta.find(key);
                    if (-1 != i) { return cache.values.get(i); }
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
                if (-1 != name.indexOf('.')) {
                    return Filesystem.file(folder, name);
                }

                // deserialize the named object
                final File file = Filesystem.file(folder, name + ext);
                if (!file.isFile()) { return null; }
                final Object r = new JSONDeserializer().run(
                    "file:///", sub(folder, path),
                    ConstArray.array(type), code,
                    Filesystem.read(file)).get(0);
                cache = cache.with(key, r);
                return r;
            }
        }
        return new ImporterX();
    }
    
    /**
     * Initializes a configuration setting.
     * @param name      setting name
     * @param value     setting value
     * @throws Exception    any problem persisting the <code>value</code>
     */
    public void
    init(final String name, final Object value) throws Exception {
        new JSONSerializer().run(export, ConstArray.array(value),
            Filesystem.writeNew(Filesystem.file(root, name + ext)));
        cache = cache.with(name, value);
    }
    
    /**
     * Creates a temporary override of a configuration setting.
     * @param name      setting name
     * @param value     transient setting value
     */
    public void
    override(final String name, final Object value) {
        Filesystem.file(root, name + ext);
        cache = cache.with(name, value);
    }
}
