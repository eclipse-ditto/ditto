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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
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
 * <p>
 * For Ditto to be able to work with models/concepts of a Feature (e. g. syntactically validate
 * properties of Features or provide detailed information about message parameters, etc.) it is possible to attach a
 * {@link FeatureDefinition}. The Definition can be compared to interface declarations in programming languages
 * (with the difference that in programming languages the type system is fully known where as in Ditto the semantics of
 * definitions is not fixed but depends on different usage scenarios like for example validation, mapping, ...).
 * </p>
 */
@Immutable
public interface Feature extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new empty builder for an immutable {@code Feature}.
     *
     * @return the builder.
     */
    static FeatureBuilder.FromScratchBuildable newBuilder() {
        return ThingsModelFactory.newFeatureBuilder();
    }

    /**
     * Returns a new builder for an immutable {@code Feature} which is initialised with the values of the this
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
     * Returns the attached Definition of this Feature.
     *
     * @return the Definition or an empty Optional.
     */
    Optional<FeatureDefinition> getDefinition();

    /**
     * Sets the specified Definition to a copy of this Feature.
     *
     * @param featureDefinition the Definition to be attached to a copy of this Feature.
     * @return a copy of this Feature with the specified Definition attached or this Feature instance if the
     * specified Definition was already set.
     * @throws NullPointerException if {@code featureDefinition} is {@code null}.
     */
    Feature setDefinition(FeatureDefinition featureDefinition);

    /**
     * Removes the Definition from a copy of this Feature.
     *
     * @return a copy of this Feature without a Definition or this Feature instance if this feature was already
     * without Definition.
     */
    Feature removeDefinition();

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
        return getProperty(JsonPointer.of(pointer));
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
        return setProperty(JsonPointer.of(pointer), propertyValue);
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
        return setProperty(JsonPointer.of(pointer), JsonValue.of(propertyValue));
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
        return setProperty(JsonPointer.of(pointer), JsonValue.of(propertyValue));
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
        return setProperty(JsonPointer.of(pointer), JsonValue.of(propertyValue));
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
        return setProperty(JsonPointer.of(pointer), JsonValue.of(propertyValue));
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
        return setProperty(JsonPointer.of(pointer), JsonValue.of(propertyValue));
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
        return removeProperty(JsonPointer.of(pointer));
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
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the Feature's {@link JsonSchemaVersion} as {@code int}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL,
                        FieldType.HIDDEN, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field definition for the Feature's Definition as {@link org.eclipse.ditto.json.JsonArray}.
         */
        public static final JsonFieldDefinition<JsonArray> DEFINITION =
                JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         *
         * JSON field definition for the Feature's properties as {@link org.eclipse.ditto.json.JsonObject}.
         */
        public static final JsonFieldDefinition<JsonObject> PROPERTIES =
                JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
