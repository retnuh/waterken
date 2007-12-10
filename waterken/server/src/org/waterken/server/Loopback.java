// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.server;

import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.waterken.net.Locator;
import org.waterken.uri.Authority;
import org.waterken.uri.Location;
import org.waterken.uri.URI;

/**
 * The loopback device.
 */
final class
Loopback {

    private
    Loopback() {}
    
    static protected final InetAddress addr;
    static {
        try {
            addr = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        } catch (final UnknownHostException e) {
            throw new AssertionError(e);
        }
    }
    
    static Locator
    client(final int standardPort) {
        class ClientX implements Locator, Serializable {
            static private final long serialVersionUID = 1L;

            public String
            canonicalize(final String authority) {
                final String location = Authority.location(authority);
                final String hostname = Location.hostname(location);
                final int port = Location.port(standardPort, location);
                return hostname.toLowerCase() +
                       (standardPort == port ? "" : ":" + port);
            }
            
            public Socket
            locate(final String host,
                   final SocketAddress mostRecent) throws IOException {
                final String authority = URI.authority(host);
                final String location = Authority.location(authority);
                final String hostname = Location.hostname(location);
                final int port = Location.port(standardPort, location);
                if (!"localhost".equalsIgnoreCase(hostname)) {
                    throw new ConnectException();
                }
                return new Socket(addr, port);
            }
        }
        return new ClientX();
    }
}
