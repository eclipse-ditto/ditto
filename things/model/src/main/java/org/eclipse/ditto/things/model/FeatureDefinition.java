/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * A FeatureDefinition is a list of fully qualified {@link DefinitionIdentifier}s.
 * A FeatureDefinition is guaranteed to contain at least one DefinitionIdentifier.
 * Each Identifier is unique, i. e. a Feature Definition does not contain duplicates.
 */
@Immutable
public interface FeatureDefinition extends Iterable<DefinitionIdentifier>, Jsonifiable<JsonArray> {

    /**
     * Parses the specified CharSequence to an Identifier and returns an immutable {@code FeatureDefinition}
     * containing that Identifier.
     *
     * @param identifier CharSequence-representation of the first Identifier of the returned FeatureDefinition.
     * @param furtherIdentifiers optional further Identifiers of the returned FeatureDefinition.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DefinitionIdentifierInvalidException if any argument is invalid.
     */
    static FeatureDefinition fromIdentifier(final CharSequence identifier, final CharSequence ... furtherIdentifiers) {
        final FeatureDefinitionBuilder builder = ThingsModelFactory.newFeatureDefinitionBuilder(identifier);
        for (final CharSequence furtherIdentifier : checkNotNull(furtherIdentifiers, "further identifiers")) {
            builder.add(furtherIdentifier);
        }
        return builder.build();
    }

    /**
     * Returns a new immutable {@code FeatureDefinition} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonArray}.
     *
     * @param jsonArrayAsString provides the initial values of the result.
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws NullPointerException if {@code jsonArrayAsString} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonArrayAsString} cannot be parsed
     * to {@code FeatureDefinition}.
     * @throws FeatureDefinitionEmptyException if the JSON array is empty.
     * @throws DefinitionIdentifierInvalidException if any Identifier of the JSON array is invalid.
     */
    static FeatureDefinition fromJson(final String jsonArrayAsString) {
        return ThingsModelFactory.newFeatureDefinition(jsonArrayAsString);
    }

    /**
     * Returns a new immutable {@code FeatureDefinition} which is initialised with the values of the given JSON array.
     *
     * @param jsonArray provides the initial values of the result.
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonArray} is empty.
     * @throws DefinitionIdentifierInvalidException if any Identifier of {@code jsonArray} is invalid.
     */
    static FeatureDefinition fromJson(final JsonArray jsonArray) {
        return ThingsModelFactory.newFeatureDefinition(jsonArray);
    }

    /**
     * Returns the first Identifier of this Feature Definition which is guaranteed to exist.
     *
     * @return the Identifier.
     */
    DefinitionIdentifier getFirstIdentifier();

    /**
     * Returns the count of Identifiers of this Feature Definition. The size is guaranteed to be at least one.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Returns a sequential {@code Stream} with the Identifiers of this Feature Definition as its source.
     *
     * @return a sequential stream of the Identifiers of this Feature Definition.
     */
    Stream<DefinitionIdentifier> stream();

}
