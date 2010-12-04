// Copyright 2008 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.remote.http;

import java.io.Serializable;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.joe_e.Token;
import org.ref_send.promise.Log;
import org.waterken.base32.Base32;
import org.waterken.db.Root;

/**
 * A {@link Session} maker.
 */
public final class
SessionMaker implements Serializable {
    static private final long serialVersionUID = 1L;

    private final Root root;
    private final Log log;
    
    protected
    SessionMaker(final Root root, final Log log) {
        this.root = root;
        this.log = log;
    }
    
    // org.waterken.remote.http.SessionMaker interface
    
    static private final String sessionKeyPrefix = ".session-";
    
    protected ServerSideSession
    open(final String key) {
        ServerSideSession r = root.fetch(null, sessionKeyPrefix + key);
        if (null == r) {
            r = new ServerSideSession(log, hash(key));
            root.assign(sessionKeyPrefix + key, r);
        }
        return r;
    }
    
    public SessionInfo
    getFresh() {
        final String key = root.export(new Token(), true);
        return new SessionInfo(key, hash(key));
    }
    
    static private String
    hash(final String x) {
        // TODO: Do something here that will pass Joe-E verification.
        try {
            final int keyChars = 128 / 5 + 1;
            final StringBuilder keyBuffer = new StringBuilder(keyChars);
            keyBuffer.append(x);
            for (int i = x.length(); i != keyChars; ++i){keyBuffer.append('a');}
            final byte[] key = Base32.decode(keyBuffer.toString());
            final byte[] plaintext = new byte[128 / Byte.SIZE];
            final byte[] cyphertext = new byte[128 / Byte.SIZE];
            final Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            aes.doFinal(plaintext, 0, plaintext.length, cyphertext);
            return Base32.encode(cyphertext).substring(0, x.length());
        } catch (final Exception e) { throw new Error(e); }        
    }
}
