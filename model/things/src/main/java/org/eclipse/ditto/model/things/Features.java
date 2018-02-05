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

import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;


/**
 * A collection of a {@link Thing}'s {@link Feature}s.
 */
@Immutable
public interface Features extends Iterable<Feature>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}.
     *
     * @return the builder.
     */
    static FeaturesBuilder newBuilder() {
        return ThingsModelFactory.newFeaturesBuilder();
    }

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}. The builder is initialised
     * with the features of this instance.
     *
     * @return the builder.
     */
    default FeaturesBuilder toBuilder() {
        return ThingsModelFactory.newFeaturesBuilder(this);
    }

    /**
     * Returns the Feature with the given ID or an empty optional.
     *
     * @param featureId the ID of the Feature to be retrieved.
     * @return the Feature with the given ID or an empty optional.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Optional<Feature> getFeature(String featureId);

    /**
     * Sets the given Feature to a copy of this Features. A previous Feature with the same ID will be overwritten.
     *
     * @param feature the Feature to be set.
     * @return a copy of this Features with {@code feature} set.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    Features setFeature(Feature feature);

    /**
     * Removes the Feature with the given ID from a copy of this Features.
     *
     * @param featureId the ID of the Feature to be removed.
     * @return a copy of this Features with {@code feature} removed.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Features removeFeature(String featureId);

    /**
     * Sets the given definition for the Feature with the given ID on a copy of this Features. The
     * previous definition of a Feature with the same ID is overwritten.
     *
     * @param featureId the ID of the Feature for which the definition is set.
     * @param definition the definition to be set.
     * @return a copy of this Features with the definition set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Features setDefinition(String featureId, FeatureDefinition definition);

    /**
     * Removes the definition from the Feature with the given ID from a copy of this Features.
     *
     * @param featureId the ID of the Feature from which the definition is removed.
     * @return a copy of this Features with the definition of the Feature with {@code featureId} removed.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Features removeDefinition(String featureId);

    /**
     * Sets the given properties for the Feature with the given ID on a copy of this Features. The
     * previous properties of a Feature with the same ID are overwritten.
     *
     * @param featureId the ID of the Feature for which the properties are set.
     * @param properties the properties to be set.
     * @return a copy of this Features with the property set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Features setProperties(String featureId, FeatureProperties properties);

    /**
     * Removes all properties of the Feature with the given ID from a copy of this Features.
     *
     * @param featureId the ID of the Feature from which all properties are removed.
     * @return a copy of this Features with all properties of the Feature with {@code featureId} removed.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Features removeProperties(String featureId);

    /**
     * Sets the value of the property which is referred by the given JSON Pointer of the Feature with the given
     * ID on a copy of this Features. The value of a previous property at the pointed position is overwritten.
     *
     * @param featureId the ID of the Feature of which the property is set.
     * @param propertyPath defines the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Features with the property set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Features setProperty(String featureId, JsonPointer propertyPath, JsonValue propertyValue);

    /**
     * Removes the property which is referred by the given JSON Pointer from the Feature with the given ID on
     * a copy of this Features..
     *
     * @param featureId the ID of the Feature from which the property is removed.
     * @param propertyPath defines the hierarchical path within the Feature to the property to be removed.
     * @return a copy of this Features with the given property removed.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Features removeProperty(String featureId, JsonPointer propertyPath);

    /**
     * Indicates whether this Features are equivalent to semantic {@code null}.
     *
     * @return {@code true} if this Features are semantically {@code null}, {@code false} else.
     */
    default boolean isNull() {
        return false;
    }

    /**
     * Returns the size of this Features, i. e. the number of contained values.
     *
     * @return the number of Features.
     */
    int getSize();

    /**
     * Indicates whether this Features is empty.
     *
     * @return {@code true} if this Features does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the values of this Features as its source.
     *
     * @return a sequential stream of the Features of this container.
     */
    Stream<Feature> stream();

    /**
     * Returns all non hidden marked fields of this Features.
     *
     * @return a JSON object representation of this Features including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
