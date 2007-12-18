// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.web_send.graph;

import java.io.Serializable;

import org.joe_e.Struct;
import org.ref_send.Record;
import org.ref_send.deserializer;
import org.ref_send.name;
import org.ref_send.promise.eventual.Eventual;

/**
 * The authority provided to the creator of a new model.
 * <p>
 * A {@link Framework} provides permissions to a model, as in the
 * Model-View-Control (MVC) pattern. An application should be composed of
 * separate classes that fall into only one of these categories. Objects forming
 * the application's model should be stored in the model to make them
 * persistent. Objects in the other categories should be transient and so
 * reconstructed each time the application is revived from its persistent state.
 * Following this convention reduces the number of classes with a persistent
 * representation that MUST be supported across application upgrade. When
 * designing classes for your model, take care to limit their complexity and
 * plan for upgrade.
 * </p>
 */
public final class
Framework extends Struct implements Record, Serializable {
    static private final long serialVersionUID = 1L;

    /**
     * eventual operator
     */
    public final Eventual _;
    
    /**
     * permission to destruct this model
     */
    public final Runnable destruct;
    
    /**
     * sub-model factory
     * <p>
     * All created models will be destructed when this model is destructed.
     * </p>
     */
    public final Spawn spawn;
    
    /**
     * permission to publish well-known references
     * <p>
     * All remote clients have read permission on this namespace.
     * </p>
     */
    public final Publisher publisher;
    
    /**
     * Constructs an instance.
     * @param _         {@link #_}
     * @param destruct  {@link #destruct}
     * @param spawn     {@link #spawn}
     * @param publisher {@link #publisher}
     */
    public @deserializer
    Framework(@name("_") final Eventual _,
              @name("destruct") final Runnable destruct,
              @name("spawn") final Spawn spawn,
              @name("publisher") final Publisher publisher) {
        this._ = _;
        this.destruct = destruct;
        this.spawn = spawn;
        this.publisher = publisher;
    }
}
