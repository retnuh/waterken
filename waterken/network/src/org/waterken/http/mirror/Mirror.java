// Copyright 2002-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.http.mirror;

import java.io.InputStream;
import java.io.Serializable;

import org.joe_e.Struct;
import org.joe_e.inert;
import org.joe_e.array.PowerlessArray;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.waterken.archive.Archive;
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
 * An HTTP mirror site.
 */
public final class
Mirror extends Struct implements Server, Serializable {
    static private final long serialVersionUID = 1L;
    
    private final Archive archive;
    private final MIME formats;
    
    /**
     * Constructs an instance.
     * @param archive   file archive
     * @param formats   each known file type
     */
    public @deserializer
    Mirror(@name("archive") @inert final Archive archive,
           @name("formats") final MIME formats) {
        this.archive = archive;
        this.formats = formats;
    }

    // org.waterken.http.Server interface
    
    public void
    serve(final Request head, final InputStream body,
                              final Client client) throws Exception {

        // determine the request target
        FileType contentType = FileType.unknown;
        Archive.Entry target; {
            final String path = URI.path(head.uri);
            final String name = Path.name(path);
            final String ext = Filename.ext(name);
            if ("".equals(ext)) {
                if (!"".equals(path)) {
                    final Archive.Entry dir = archive.find(path + "/");
                    if (null != dir) {
                        client.receive(new Response(
                            "HTTP/1.1", "307", "Temporary Redirect",
                            PowerlessArray.array(
                                new Header("Location", head.uri + "/"),
                                new Header("Content-Length", "0")
                            )), null);
                        return;
                    }
                }
                target = null;
                final String pathname =
                    Path.folder(path) + ("".equals(name) ? "index" : name);
                for (final FileType t : formats.known) {
                    final Archive.Entry x = archive.find(pathname + t.ext);
                    if (null != x) {
                        target = x;
                        contentType = t;
                        break;
                    }
                }
            } else {
                target = archive.find(path);
                if (null != target) {
                    for (final FileType format : formats.known) {
                        if (Header.equivalent(format.ext, ext)) {
                            contentType = format;
                            break;
                        }
                    }
                }
            }
        }
        if (null == target) {
            client.receive(Response.notFound(), null);
            return;
        }

        // obey any request restrictions
        final String etag = target.getETag();
        if (!head.respond(etag,client,"GET","HEAD","OPTIONS","TRACE")) {return;}

        // output the corresponding representation
        final String promise = Query.arg(null, URI.query("", head.uri), "o");
        final InputStream in = target.open();
        try {
            PowerlessArray<Header> header = PowerlessArray.array(
                new Header("ETag", etag),
                new Header("Cache-Control",
                           null != promise ? "max-age=" + forever : "no-cache"),
                new Header("Content-Length", "" + target.getLength()),
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
