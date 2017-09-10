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


import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;


/**
 * <p>
 * A Feature is used to manage all data and functionality of a {@link Thing} that can be clustered in an outlined
 * technical context.
 * </p>
 * <p>
 * For different contexts or aspects of a Thing, different Features can be used which are all belonging to the
 * same Thing and do not exists without this Thing.
 * </p>
 * <p>
 * The <em>data</em> related to Features is managed in form of {@link FeatureProperties}. These properties can
 * be categorized, e. g. to manage the status, the configuration or any fault information.
 * </p>
 */
@Immutable
public interface Feature extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new empty builder for an immutable {@link Feature}.
     *
     * @return the builder.
     */
    static FeatureBuilder.FromScratchBuildable newBuilder() {
        return ThingsModelFactory.newFeatureBuilder();
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the this
     * Feature object.
     *
     * @return the new builder.
     */
    default FeatureBuilder.FromCopyBuildable toBuilder() {
        return ThingsModelFactory.newFeatureBuilder(this);
    }

    /**
     * Returns the ID of this Feature.
     *
     * @return the ID of this Feature.
     */
    String getId();

    /**
     * Returns the properties of this Feature.
     *
     * @return the properties of this Feature.
     */
    Optional<FeatureProperties> getProperties();

    /**
     * Sets the given properties to a copy of this Feature. The previous properties of the Feature are overwritten.
     *
     * @param properties the properties to be set.
     * @return a copy of this Feature with the given properties.
     * @throws NullPointerException if {@code properties} is {@code null}.
     */
    Feature setProperties(FeatureProperties properties);

    /**
     * Removes all properties on a copy of this Feature.
     *
     * @return a copy of this Feature with all of its properties removed.
     */
    Feature removeProperties();

    /**
     * Gets the property value which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be returned.
     * @return the value of the property which is referred by {@code pointer}.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    default Optional<JsonValue> getProperty(final CharSequence pointer) {
        return getProperty(JsonPointer.newInstance(pointer));
    }

    /**
     * Gets the property value which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be returned.
     * @return the value of the property which is referred by {@code pointer}.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    Optional<JsonValue> getProperty(JsonPointer pointer);

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final JsonValue propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), propertyValue);
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final boolean propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), JsonValue.newInstance(propertyValue));
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final int propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), JsonValue.newInstance(propertyValue));
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final long propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), JsonValue.newInstance(propertyValue));
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final double propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), JsonValue.newInstance(propertyValue));
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Feature setProperty(final CharSequence pointer, final String propertyValue) {
        return setProperty(JsonPointer.newInstance(pointer), JsonValue.newInstance(propertyValue));
    }

    /**
     * Sets the value of a property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the property value to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Feature with the given property value set.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Feature setProperty(JsonPointer pointer, JsonValue propertyValue);

    /**
     * Removes the property specified by a JSON Pointer from a copy of this Feature.
     *
     * @param pointer defines the hierarchical path to the property to be removed.
     * @return a copy of this Feature with the specified property removed.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    default Feature removeProperty(final CharSequence pointer) {
        return removeProperty(JsonPointer.newInstance(pointer));
    }

    /**
     * Removes the property specified by a JSON Pointer from a copy of this Feature.
     *
     * @param pointer defines the hierarchical path to the property to be removed.
     * @return a copy of this Feature with the specified property removed.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    Feature removeProperty(JsonPointer pointer);

    /**
     * Returns all non hidden marked fields of this Feature.
     *
     * @return a JSON object representation of this Feature including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a Feature.
     *
     */
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition SCHEMA_VERSION =
                newFieldDefinition(JsonSchemaVersion.getJsonKey(), int.class, FieldType.SPECIAL, FieldType.HIDDEN,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Feature's properties.
         */
        public static final JsonFieldDefinition PROPERTIES =
                newFieldDefinition("properties", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
