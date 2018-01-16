/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A FeatureDefinition is a list of fully qualified {@link Identifier}s. A FeatureDefinition is guaranteed to contain
 * at least one identifier. Each identifier is unique, i. e. a feature definition does not contain duplicates.
 */
public interface FeatureDefinition extends Iterable<FeatureDefinition.Identifier>, Jsonifiable<JsonArray> {

    /**
     * Returns the first Identifier of this feature definition which is guaranteed to exist.
     *
     * @return the Identifier.
     */
    Identifier getFirstIdentifier();

    /**
     * Returns the count of identifiers of this feature definition. The size is guaranteed to be at least one.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Returns a sequential {@code Stream} with the identifiers of this feature definition as its source.
     *
     * @return a sequential stream of the identifiers of this feature definition.
     */
    Stream<Identifier> stream();

    /**
     * This interface represents a single fully qualified identifier of a {@code FeatureDefinition}.
     */
    interface Identifier {

        /**
         * Returns the namespace of ths identifier.
         *
         * @return the namespace.
         */
        String getNamespace();

        /**
         * Returns the name of this identifier.
         *
         * @return the name.
         */
        String getName();

        /**
         * Returns the version of this identifier.
         *
         * @return the version.
         */
        String getVersion();

    }

}
