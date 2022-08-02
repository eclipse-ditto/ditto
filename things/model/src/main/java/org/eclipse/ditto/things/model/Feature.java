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

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

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
     * Returns the desired properties of this Feature.
     *
     * @return the desired properties of this Feature.
     * @since 1.5.0
     */
    Optional<FeatureProperties> getDesiredProperties();

    /**
     * Sets the given desired properties to a copy of this Feature.
     * The previous desired properties of the Feature are overwritten.
     *
     * @param desiredProperties the desired properties to be set.
     * @return a copy of this Feature with the given properties.
     * @throws NullPointerException if {@code desiredProperties} is {@code null}.
     * @since 1.5.0
     */
    Feature setDesiredProperties(FeatureProperties desiredProperties);

    /**
     * Removes all desired properties on a copy of this Feature.
     *
     * @return a copy of this Feature with all of its desired properties removed.
     * @since 1.5.0
     */
    Feature removeDesiredProperties();

    /**
     * Gets the desired property value which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be returned.
     * @return the value of the desired property which is referred by {@code pointer}.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @since 1.5.0
     */
    default Optional<JsonValue> getDesiredProperty(final CharSequence pointer) {
        return getDesiredProperty(JsonPointer.of(pointer));
    }

    /**
     * Gets the desired property value which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be returned.
     * @return the value of the desired property which is referred by {@code pointer}.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @since 1.5.0
     */
    Optional<JsonValue> getDesiredProperty(JsonPointer pointer);

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final JsonValue desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), desiredPropertyValue);
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final boolean desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final int desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final long desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final double desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Feature setDesiredProperty(final CharSequence pointer, final String desiredPropertyValue) {
        return setDesiredProperty(JsonPointer.of(pointer), JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the value of a desired property which is referred by the given JSON Pointer.
     *
     * @param pointer defines the hierarchical path to the desired property value to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Feature with the given desired property value set.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    Feature setDesiredProperty(JsonPointer pointer, JsonValue desiredPropertyValue);

    /**
     * Removes the desired property specified by a JSON Pointer from a copy of this Feature.
     *
     * @param pointer defines the hierarchical path to the desired property to be removed.
     * @return a copy of this Feature with the specified desired property removed.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @since 1.5.0
     */
    default Feature removeDesiredProperty(final CharSequence pointer) {
        return removeDesiredProperty(JsonPointer.of(pointer));
    }

    /**
     * Removes the desired property specified by a JSON Pointer from a copy of this Feature.
     *
     * @param pointer defines the hierarchical path to the desired property to be removed.
     * @return a copy of this Feature with the specified desired property removed.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @since 1.5.0
     */
    Feature removeDesiredProperty(JsonPointer pointer);

    /**
     * Returns all non-hidden marked fields of this Feature.
     *
     * @return a JSON object representation of this Feature including only non-hidden marked fields.
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
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION = JsonFactory.newIntFieldDefinition(
                JsonSchemaVersion.getJsonKey(),
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        /**
         * JSON field definition for the Feature's Definition as {@link org.eclipse.ditto.json.JsonArray}.
         */
        public static final JsonFieldDefinition<JsonArray> DEFINITION =
                JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field definition for the Feature's properties as {@link org.eclipse.ditto.json.JsonObject}.
         */
        public static final JsonFieldDefinition<JsonObject> PROPERTIES =
                JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field definition for the Feature's desired properties as {@link org.eclipse.ditto.json.JsonObject}.
         *
         * @since 1.5.0
         */
        public static final JsonFieldDefinition<JsonObject> DESIRED_PROPERTIES =
                JsonFactory.newJsonObjectFieldDefinition("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
