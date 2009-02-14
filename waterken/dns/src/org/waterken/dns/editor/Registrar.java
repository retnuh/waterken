// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.dns.editor;

import org.ref_send.promise.Promise;
import org.ref_send.promise.Vat;
import org.waterken.dns.Resource;
import org.waterken.menu.Menu;

/**
 * A {@link Resource} {@link Menu} factory.
 */
public interface
Registrar {

    /**
     * Registers a hostname.
     * @param hostname  hostname
     * @return administrator permissions for the host
     * @throws RuntimeException <code>hostname</code> already claimed
     */
    Promise<Vat<Menu<Resource>>> claim(String hostname) throws RuntimeException;
}
