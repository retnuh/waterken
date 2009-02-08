// Copyright 2002-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.http.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;

import org.joe_e.Struct;
import org.joe_e.array.PowerlessArray;
import org.joe_e.file.Filesystem;
import org.joe_e.file.InvalidFilenameException;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.waterken.http.Client;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.http.Server;
import org.waterken.io.FileType;
import org.waterken.io.MIME;
import org.waterken.uri.Filename;
import org.waterken.uri.Header;
import org.waterken.uri.Path;
import org.waterken.uri.Query;
import org.waterken.uri.URI;

/**
 * An HTTP file server.
 */
public final class
Files extends Struct implements Server, Serializable {
    static private final long serialVersionUID = 1L;

    private final File folder;
    private final Tag tag;
    private final MIME formats;
    
    /**
     * Constructs an instance.
     * @param folder    folder
     * @param tag       ETag generator
     * @param formats   each known file type
     */
    public @deserializer
    Files(@name("folder") final File folder,
          @name("tag") final Tag tag,
          @name("formats") final MIME formats) {
        this.folder = folder;
        this.tag = tag;
        this.formats = formats;
    }
    
    public void
    serve(final Request head,
          final InputStream body, final Client client) throws Exception {        

        // determine the request target
        FileType contentType = FileType.unknown;
        final File file;
        try {
            final String name = Path.name(URI.path(head.uri));
            final String filename = "".equals(name) ? "index" : name;
            final String ext = Filename.ext(filename);
            if (filename.startsWith(".")) { throw new FileNotFoundException(); }
            final File exact = Filesystem.file(folder, filename);
            if (exact.isFile()) {
                file = exact;
                for (final FileType format : formats.known) {
                    if (Header.equivalent(format.ext, ext)) {
                        contentType = format;
                        break;
                    }
                }
            } else if (exact.isDirectory()) {
                client.receive(new Response(
                    "HTTP/1.1", "307", "Temporary Redirect",
                    PowerlessArray.array(
                        new Header("Location", head.uri + "/"),
                        new Header("Content-Length", "0")
                    )), null);
                return;
            } else if ("".equals(ext)) {
                File negotiated = null;
                for (final FileType format : formats.known) {
                    final File f = Filesystem.file(folder, filename+format.ext);
                    if (f.isFile()) {
                        negotiated = f;
                        contentType = format;
                        break;
                    }
                }
                if (null == negotiated) { throw new FileNotFoundException(); }
                file = negotiated;
            } else { throw new FileNotFoundException(); }
        } catch (final FileNotFoundException e) {
            client.receive(Response.notFound(), null);
            return;
        } catch (final InvalidFilenameException e) {
            client.receive(Response.gone(), null);
            return;
        }

        // obey any request restrictions
        final String etag = tag.run(file); 
        if (!head.respond(etag,client,"GET","HEAD","OPTIONS","TRACE")) {return;}

        // output the corresponding representation
        final String promise = Query.arg(null, URI.query("", head.uri), "o");
        final InputStream in = Filesystem.read(file);
        try {
            PowerlessArray<Header> header = PowerlessArray.array(
                new Header("ETag", etag),
                new Header("Cache-Control",
                           null != promise ? "max-age=" + forever : "no-cache"),
                new Header("Content-Length", "" + Filesystem.length(file)),
                new Header("Content-Type", contentType.name)
            );
            if (null != contentType.encoding) {
                header = header.with(new Header("Content-Encoding",
                                                contentType.encoding));
            }
            client.receive(new Response("HTTP/1.1", "200", "OK", header),
                           "HEAD".equals(head.method) ? null : in);
        } catch (final Exception e) {
            try { in.close(); } catch (final Exception e2) {}
            throw e;
        }
        in.close();
    }
}
