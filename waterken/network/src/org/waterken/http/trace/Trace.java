// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.http.trace;

import java.io.Serializable;

import org.joe_e.Struct;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.ref_send.promise.eventual.Do;
import org.waterken.http.Request;
import org.waterken.http.Response;
import org.waterken.http.Server;
import org.waterken.uri.URI;

/**
 * A server that does a <code>TRACE</code> on a <code>GET</code> request.  
 */
public final class
Trace extends Struct implements Server, Serializable {
    static private final long serialVersionUID = 1L;

    private final String prefix;
    private final Server next;

    /**
     * Constructs an instance.
     * @param prefix    path prefix to trace
     * @param next      next server to try
     */
    public @deserializer
    Trace(@name("prefix") final String prefix,
          @name("next") final Server next) {
        this.prefix = prefix;
        this.next = next;
    }
    
    // org.waterken.http.Server interface

    public void
    serve(final String resource, final Request request,
                                 final Do<Response,?> respond) throws Exception{        
    
        // further dispatch the request
        if (!URI.path(resource).startsWith(prefix)) {
            next.serve(resource, request, respond);
            return;
        }
        
        // reached the final message processor, so bounce a trace
        if ("TRACE".equals(request.head.method)) {
            respond.fulfill(request.trace());
            return;
        }

        // obey any request restrictions
        if (!request.allow(null, respond, "GET", "HEAD", "OPTIONS", "TRACE")) {
            return;
        }
        
        respond.fulfill(request.trace());
    }
}
