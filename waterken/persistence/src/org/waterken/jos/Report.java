// Copyright 2006 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.jos;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamConstants;
import java.io.PrintStream;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import org.joe_e.reflect.Reflection;
import org.waterken.archive.Archive;
import org.waterken.archive.n2v.N2V;

/**
 * Reports on the content of an archive.
 */
/* package */ final class
Report {
    private Report() {}

    /**
     * The command line arguments are:
     * <ol>
     *  <li>path to .n2v format file</li>
     * </ol>
     * @param args  argument string
     */
    static public void
    main(final String[] args) throws Exception {
        final PrintStream stdout = System.out;
        if (0 == args.length) {
            stdout.println("Reports on the content of an archive.");
            stdout.println("use: java -jar report.jar <file-path>");
            System.exit(-1);
            return;
        }
        final File file = new File(args[0]);
        
        final Archive archive = N2V.open(file);
        report(stdout, archive);
        stdout.flush();
        stdout.close();
        archive.close();
    }

    static private final int minNameWidth = 26 + JODB.ext.length();

    static private final class
    Total {
        final String typename;
        int entries;
        long bytes;

        Total(final String typename) {
            this.typename = typename;
        }
    }
    
    static private void
    report(final PrintStream stdout, final Archive archive) throws Exception {
        final HashMap<String,Total> total = new HashMap<String,Total>();
        stdout.println("--- Entries ( name, length, typename) ---");
        for (final Archive.Entry entry : archive) {
            stdout.print(entry.getName());
            for (int n = minNameWidth - entry.getName().length(); 0 < n--;) {
                stdout.print(' ');
            }
            stdout.print('\t');
            stdout.print(entry.getLength());
            stdout.print('\t');
            final String typename = identify(entry.open());
            stdout.println(typename);

            // keep track of totals
            Total t = total.get(typename);
            if (null == t) { total.put(typename, t = new Total(typename)); }
            t.entries += 1;
            t.bytes += entry.getLength();
        }
        stdout.println();
        stdout.println("--- Totals ( entries, bytes, typename) ---");
        final Total[] sum = total.values().toArray(new Total[total.size()]);
        Arrays.sort(sum, new Comparator<Total>() {
            public int
            compare(final Total a, final Total b) {
                return a.entries == b.entries
                    ? (a.bytes == b.bytes
                        ? a.typename.compareTo(b.typename)
                        : (a.bytes > b.bytes ? -1 : 1))
                    : (a.entries > b.entries ? -1 : 1);
            }
        });
        int entries = 0;
        long bytes = 0L;
        for (final Total t : sum) {
            stdout.print(t.entries);
            stdout.print('\t');
            stdout.print(t.bytes);
            stdout.print('\t');
            stdout.print(t.typename);
            stdout.println();

            entries += t.entries;
            bytes += t.bytes;
        }

        stdout.println();
        stdout.println("--- Total ---");
        stdout.println("entries:\t" + entries);
        stdout.println("bytes:\t" + bytes);
        stdout.println("types:\t" + sum.length);
    }
    
    /**
     * Determine the type of object stored in a stream.
     */
    static private String
    identify(final InputStream s) {
        final DataInputStream data = new DataInputStream(s);
        String r;
        try {
            if (ObjectStreamConstants.STREAM_MAGIC != data.readShort()) {
                throw new StreamCorruptedException();
            }
            data.readShort();   // skip version number, assume compatible
            switch (data.read()) {
            case ObjectStreamConstants.TC_OBJECT: {
                switch (data.read()) {
                case ObjectStreamConstants.TC_CLASSDESC: { r = data.readUTF(); }
                break;
                case ObjectStreamConstants.TC_PROXYCLASSDESC: {
                    r = data.readInt() > 0 ? data.readUTF() : "proxy";
                }
                break;
                default: throw new StreamCorruptedException();
                }
            }
            break;
            case ObjectStreamConstants.TC_ARRAY: { r = "array"; }
            break;
            case ObjectStreamConstants.TC_STRING: { r = "string"; }
            break;
            case ObjectStreamConstants.TC_LONGSTRING: { r = "long string"; }
            break;
            case ObjectStreamConstants.TC_NULL: { r = "null"; }
            break;
            default: throw new StreamCorruptedException();
            }
        } catch (final IOException e) {
            r = "! " + Reflection.getName(e.getClass());
        }
        try { data.close(); } catch (final Exception e) {}
        return r;
    }
}
