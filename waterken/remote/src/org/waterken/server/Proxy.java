// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.server;

import java.io.InputStream;
import java.io.Serializable;

import org.joe_e.Struct;
import org.waterken.cache.Cache;
import org.waterken.http.Client;
import org.waterken.http.Request;
import org.waterken.http.Server;
import org.waterken.http.TokenList;
import org.waterken.net.Locator;
import org.waterken.net.http.ClientSide;
import org.waterken.thread.Loop;
import org.waterken.thread.Sleep;
import org.waterken.uri.Header;
import org.waterken.uri.Location;

/**
 * The HTTPS gateway for this JVM.
 */
/* package */ final class
Proxy extends Struct implements Server, Serializable {
    static private final long serialVersionUID = 1L;

    static private final Cache<String,Server> connections = Cache.make();
    
    static private synchronized Server
    connect(final String peer, final Locator transport) {
        Server r = connections.fetch(null, peer);
        if (null == r) {
            final Loop<ClientSide.Outbound> sender = Loop.make("->" + peer);
            final Loop<ClientSide.Inbound> receiver = Loop.make("<-" + peer);
            r = ClientSide.make(peer, transport, new Sleep(),
                                sender.foreground, receiver.foreground);
            connections.put(peer, r);
        }
        return r;
    }

    static protected final Credentials credentials = Settings.keys.isFile()
        ? SSL.keystore("TLS", Settings.keys, "nopass") : null;
    static private final Locator https = SSL.client(443,credentials,System.out);
    static private final Locator http = Loopback.client(80, System.out);
    
    public void
    serve(final Request head,
          final InputStream body, final Client client) throws Exception {
        // TODO: implement boot comm
        final String host = TokenList.find("localhost", "Host", head.headers);
        final Locator transport =
            Header.equivalent("localhost", Location.hostname(host))?http:https;
        connect(transport.canonicalize(host),transport).serve(head,body,client);
    }
}
