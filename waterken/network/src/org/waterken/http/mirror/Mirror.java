// Copyright 2002-2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.http.mirror;

import java.io.File;
import java.io.Serializable;

import org.joe_e.Struct;
import org.joe_e.array.PowerlessArray;
import org.joe_e.file.Filesystem;
import org.ref_send.promise.Volatile;
import org.ref_send.promise.eventual.Do;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.http.Server;
import org.waterken.http.file.Files;
import org.waterken.io.MediaType;
import org.waterken.uri.Path;
import org.waterken.uri.URI;
import org.web_send.Failure;

/**
 * An HTTP mirror site.
 */
public final class
Mirror {

    private
    Mirror() {}

    /**
     * Constructs an instance.
     * @param maxAge    max-age value
     * @param root      root folder
     * @param MIME      each known file type
     */
    static public Server
    make(final int maxAge,
         final File root,
         final PowerlessArray<MediaType> MIME) {
        class ServerX extends Struct implements Server, Serializable {
            static private final long serialVersionUID = 1L;

            public void
            serve(final String resource,
                  final Volatile<Request> request,
                  final Do<Response,?> respond) throws Exception {
                File f = root;
                for (final String segment : Path.walk(URI.path(resource))) {
                    if (segment.startsWith(".")) {
                        respond.reject(Failure.gone());
                        return;
                    }
                    f = Filesystem.file(f, segment);
                }
                Files.make(maxAge, f, MIME).serve(resource, request, respond);
            }
        }
        return new ServerX();
    }
}
