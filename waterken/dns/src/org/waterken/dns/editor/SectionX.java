// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.dns.editor;

import static org.ref_send.Slot.var;

import java.io.Serializable;

import org.joe_e.array.ByteArray;
import org.joe_e.array.ConstArray;
import org.ref_send.Slot;
import org.ref_send.Variable;
import org.ref_send.promise.Promise;
import org.waterken.dns.Resource;

/**
 * A {@link Section} implementation.
 */
final class
SectionX implements Section, Serializable {
    static private final long serialVersionUID = 1L;

    private ConstArray<Slot<Resource>> slots = ConstArray.array();

    public ConstArray<Slot<Resource>>
    getEntries() { return slots; }

    public Slot<Resource>
    add() {
        final ByteArray addr = ByteArray.array(new byte[] { 127, 0, 0, 1 });
        final Resource initial = new Resource(Resource.A, Resource.IN, 0, addr);
        final Slot<Resource> slot = var(initial); 
        slots = slots.with(slot);
        return slot;
    }

    @SuppressWarnings("unchecked") public void
    remove(final Variable<? extends Promise<Resource>> editor) {
        final Slot<Resource>[] v = new Slot[slots.length() - 1];
        int i = 0;
        for (final Slot<Resource> slot : slots) {
            if (!slot.equals(editor)) {
                v[i++] = slot;
            }
        }
        slots = ConstArray.array(v);
    }
}
