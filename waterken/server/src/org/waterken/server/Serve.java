// Copyright 2005-2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.server;

import static org.joe_e.file.Filesystem.file;
import static org.waterken.io.MediaType.MIME;

import java.io.File;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;

import org.waterken.dns.udp.NameServer;
import org.waterken.http.Server;
import org.waterken.http.mirror.Mirror;
import org.waterken.http.trace.Trace;
import org.waterken.jos.JODB;
import org.waterken.net.http.HTTPD;
import org.waterken.remote.http.AMP;
import org.waterken.remote.mux.Mux;

/**
 * Starts the server.
 */
final class
Serve {

    private
    Serve() {}
    
    /**
     * @param args  command line arguments
     */
    static public void
    main(String[] args) throws Exception {
        
        // Initialize the static state.
        final File home = new File("").getAbsoluteFile();
        final File www = file(home, "www");
        final File db = file(home, JODB.dbDirName);
        final File keys = new File(home, "keys.jks");

        // Extract the arguments.
        int i = 0;
        int backlog = 100;
        int maxAge = 0;
        int soTimeout = 60 * 1000;
        if (args.length == 0) {
            // the default arguments if none are specified
            if (keys.isFile()) {
                args = new String[] { "http=80", "https=443" };
            } else {
                args = new String[] { "http=8080" };
            }
        } else {
            // Pop any server arguments.
            for (; i != args.length; ++i) {
                if (args[i].startsWith("backlog=")) {
                    backlog = Integer.parseInt(args[i].substring(9));
                } else if (args[i].startsWith("max-age=")) {
                    maxAge = Integer.parseInt(args[i].substring(8));
                } else if (args[i].startsWith("so-timeout=")) {
                    soTimeout = Integer.parseInt(args[i].substring(11));
                } else {
                    // Assume this is the start of the service list.
                    break;
                }
            }
        }

        // Summarize the configuration information.
        final PrintStream err = System.err;
        err.println("Home directory: <" + home + ">");
        err.println("Files served with Cache-Control: max-age=" + maxAge);
        err.println("Using server socket backlog: " + backlog);
        err.println("Using connection socket timeout: " + soTimeout + " ms");

        // Configure the server.
        final Server server = Trace.make(Mux.make(db, new AMP(),
                                           Mirror.make(maxAge, www, MIME)));

        // Start the inbound network services.
        for (; i != args.length; ++i) {

            final String service = args[i];
            final int eq = service.indexOf('=');
            final String protocol = service.substring(0, eq);
            final int portNumber = Integer.parseInt(service.substring(eq + 1));

            final Runnable listener;
            if ("http".equals(protocol)) {
                Proxy.protocols.put("http", Loopback.client(80));
                listener = new TCP(err, new ThreadGroup(service), "http",
                    HTTPD.make("http", server, Proxy.thread), soTimeout,
                    new ServerSocket(portNumber, backlog, Loopback.addr));
            } else if ("https".equals(protocol)) {
                final Credentials credentials=SSL.keystore("TLS",keys,"nopass");
                Proxy.protocols.put("https", SSL.client(443, credentials));
                listener = new TCP(err, new ThreadGroup(service), "https",
                    HTTPD.make("https", server, Proxy.thread), soTimeout,
                    credentials.getContext().getServerSocketFactory().
                        createServerSocket(portNumber, backlog));
            } else if ("dns".equals(protocol)) {
                listener = new UDP(err, "dns",
                    NameServer.make(file(db, "dns")),
                    new DatagramSocket(portNumber));
            } else {
                err.println("Unrecognized protocol: " + protocol);
                return;
            }

            // run the corresponding daemon
            new Thread(listener, service).start();
        }
    }
}
