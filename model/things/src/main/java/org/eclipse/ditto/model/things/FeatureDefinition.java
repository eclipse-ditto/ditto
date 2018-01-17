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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A FeatureDefinition is a list of fully qualified {@link Identifier}s. A FeatureDefinition is guaranteed to contain
 * at least one identifier. Each identifier is unique, i. e. a feature definition does not contain duplicates.
 */
@Immutable
public interface FeatureDefinition extends Iterable<FeatureDefinition.Identifier>, Jsonifiable<JsonArray> {

    /**
     * Parses the specified CharSequence to an Identifier and returns an immutable {@code FeatureDefinition}
     * containing that identifier.
     *
     * @param identifier CharSequence-representation of the first Identifier of the returned FeatureDefinition.
     * @return the instance.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code identifier} is invalid.
     */
    static FeatureDefinition fromIdentifier(final CharSequence identifier) {
        return ThingsModelFactory.newFeatureDefinitionBuilder(identifier).build();
    }

    /**
     * Returns a new immutable {@code FeatureDefinition} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonArray}.
     *
     * @param jsonArrayAsString provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws NullPointerException if {@code jsonArrayAsString} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code jsonArrayAsString} cannot be parsed
     * to {@code FeatureDefinition}.
     * @throws IllegalArgumentException if the JSON array is empty.
     * @throws FeatureDefinitionIdentifierInvalidException if any identifier of the JSON array is invalid.
     */
    static FeatureDefinition fromJson(final String jsonArrayAsString) {
        return ThingsModelFactory.newFeatureDefinition(jsonArrayAsString);
    }

    /**
     * Returns a new immutable {@code FeatureDefinition} which is initialised with the values of the given JSON array.
     *
     * @param jsonArray provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonArray} is empty.
     * @throws FeatureDefinitionIdentifierInvalidException if any identifier of {@code jsonArray} is invalid.
     */
    static FeatureDefinition fromJson(final JsonArray jsonArray) {
        return ThingsModelFactory.newFeatureDefinition(jsonArray);
    }

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
    interface Identifier extends CharSequence {

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

        /**
         * Returns the string representation of this identifier with the following structure:
         * {@code "namespace:name:version"}
         *
         * @return the string representation.
         */
        @Override
        String toString();

    }

}
