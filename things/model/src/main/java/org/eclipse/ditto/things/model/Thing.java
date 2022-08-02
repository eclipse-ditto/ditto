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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * A generic entity which can be used as a "handle" for multiple {@link Feature}s belonging to this Thing. A Thing can
 * be for example:
 * <ul>
 *    <li>a physical device like a lawn mower, a sensor, a vehicle or a lamp;</li>
 *    <li>
 *        a virtual device like a room in a house, a virtual power plant spanning multiple power plants, the weather
 *        information for a specific location collected by various sensors;
 *    </li>
 *    <li>
 *        a transactional entity like a tour of a vehicle (from start until stop) or a series of measurements of a
 *        machine;
 *    </li>
 *    <li>
 *        a master data entity like a supplier of devices or a service provider for devices or an entity representing a
 *        city/region;
 *    </li>
 *    <li>
 *        anything else &ndash; if it can be modeled and managed appropriately by the supported concepts/capabilities of
 *        Ditto.
 *    </li>
 * </ul>
 */
@Immutable
public interface Thing extends Entity<ThingRevision> {

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code Thing} from scratch.
     *
     * @return the new builder.
     */
    static ThingBuilder.FromScratch newBuilder() {
        return ThingsModelFactory.newThingBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for immutable {@code Thing}. The builder is initialised with the
     * entries of this instance.
     *
     * @return the new builder.
     */
    default ThingBuilder.FromCopy toBuilder() {
        return ThingsModelFactory.newThingBuilder(this);
    }

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return JsonSchemaVersion.LATEST;
    }

    /**
     * Returns the namespace this Thing was created in. The namespace is derived from the ID of this Thing.
     *
     * @return the namespace this Thing was created in.
     */
    Optional<String> getNamespace();

    /**
     * Returns the attributes of this Thing.
     *
     * @return the attributes of this Thing.
     */
    Optional<Attributes> getAttributes();

    /**
     * Sets the attributes on a copy of this Thing.
     *
     * @param attributes the attributes.
     * @return a copy of this Thing with the given attributes.
     */
    Thing setAttributes(@Nullable Attributes attributes);

    /**
     * Removes all attributes from a copy of this Thing.
     *
     * @return a copy of this Thing with all of its attributes removed.
     */
    Thing removeAttributes();

    @Override
    Optional<ThingId> getEntityId();

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final JsonValue attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), attributeValue);
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final boolean attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), JsonValue.of(attributeValue));
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final int attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), JsonValue.of(attributeValue));
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final long attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), JsonValue.of(attributeValue));
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final double attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), JsonValue.of(attributeValue));
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    default Thing setAttribute(final CharSequence attributePath, final String attributeValue) {
        return setAttribute(JsonPointer.of(attributePath), JsonValue.of(attributeValue));
    }

    /**
     * Sets the given attribute on a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute value.
     * @param attributeValue the attribute value to be set.
     * @return a copy of this Thing with the given attribute.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code attributePath} is empty.
     */
    Thing setAttribute(JsonPointer attributePath, JsonValue attributeValue);

    /**
     * Removes the attribute at the given path from a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute to be removed.
     * @return a copy of this Thing without the removed attribute.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    default Thing removeAttribute(final CharSequence attributePath) {
        return removeAttribute(JsonPointer.of(attributePath));
    }

    /**
     * Removes the attribute at the given path from a copy of this Thing.
     *
     * @param attributePath the hierarchical path to the attribute to be removed.
     * @return a copy of this Thing without the removed attribute.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    Thing removeAttribute(JsonPointer attributePath);

    /**
     * Gets the definition of this Thing.
     *
     * @return the Definition of this Thing.
     */
    Optional<ThingDefinition> getDefinition();

    /**
     * Sets the definition on a copy of this Thing.
     *
     * @param definitionIdentifier the Thing's definition to set.
     * @return a copy of this Thing with the given definition.
     * @throws DefinitionIdentifierInvalidException if {@code definitionIdentifier} is invalid.
     */
    Thing setDefinition(@Nullable CharSequence definitionIdentifier);

    /**
     * Removes the Thing's definition on a copy of this Thing.
     *
     * @return a copy of this Thing without definition.
     */
    Thing removeDefinition();

    /**
     * Sets the given definition of a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param definition the definition to be set.
     * @return a copy of this Thing with the Feature containing the given definition.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Thing setFeatureDefinition(String featureId, FeatureDefinition definition);

    /**
     * Removes the definition from the Feature of this thing with the specified feature ID.
     *
     * @param featureId the identifier of the Feature to delete the definition from.
     * @return a copy of this Thing with the Feature without definition.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Thing removeFeatureDefinition(String featureId);

    /**
     * Sets the given properties of a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param properties the properties to be set.
     * @return a copy of this Thing with the Feature containing the given properties.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Thing setFeatureProperties(String featureId, FeatureProperties properties);

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath,
            final JsonValue propertyValue) {

        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), propertyValue);
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath,
            final boolean propertyValue) {

        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), JsonValue.of(propertyValue));
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath, final int propertyValue) {
        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), JsonValue.of(propertyValue));
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath,
            final long propertyValue) {

        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), JsonValue.of(propertyValue));
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath,
            final double propertyValue) {

        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), JsonValue.of(propertyValue));
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing setFeatureProperty(final String featureId, final CharSequence propertyPath,
            final String propertyValue) {

        return setFeatureProperty(featureId, JsonPointer.of(propertyPath), JsonValue.of(propertyValue));
    }

    /**
     * Sets the given property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be set.
     * @param propertyValue the property value to be set.
     * @return a copy of this Thing with the Feature containing the given property.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Thing setFeatureProperty(String featureId, JsonPointer propertyPath, JsonValue propertyValue);

    /**
     * Removes all properties from the given Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature of which all properties are to be removed.
     * @return a copy of this Thing with all of the Feature's properties removed.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    Thing removeFeatureProperties(String featureId);

    /**
     * Removes the given property from a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be removed.
     * @return a copy of this Thing with the given Feature property removed.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default Thing removeFeatureProperty(final String featureId, final CharSequence propertyPath) {
        return removeFeatureProperty(featureId, JsonPointer.of(propertyPath));
    }

    /**
     * Removes the given property from a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the property to be removed.
     * @return a copy of this Thing with the given Feature property removed.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Thing removeFeatureProperty(String featureId, JsonPointer propertyPath);

    /**
     * Sets the given desired properties of a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredProperties the desired properties to be set.
     * @return a copy of this Thing with the Feature containing the given desired properties.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @since 1.5.0
     */
    Thing setFeatureDesiredProperties(CharSequence featureId, FeatureProperties desiredProperties);

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final JsonValue desiredPropertyValue) {

        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath), desiredPropertyValue);
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final boolean desiredPropertyValue) {

        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath),
                JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final int desiredPropertyValue) {
        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath),
                JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final long desiredPropertyValue) {

        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath),
                JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final double desiredPropertyValue) {

        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath),
                JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing setFeatureDesiredProperty(final CharSequence featureId, final CharSequence desiredPropertyPath,
            final String desiredPropertyValue) {

        return setFeatureDesiredProperty(featureId, JsonPointer.of(desiredPropertyPath),
                JsonValue.of(desiredPropertyValue));
    }

    /**
     * Sets the given desired property to the Feature with the given ID on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param desiredPropertyPath the hierarchical path within the Feature to the desired property to be set.
     * @param desiredPropertyValue the desired property value to be set.
     * @return a copy of this Thing with the Feature containing the given desired property.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    Thing setFeatureDesiredProperty(CharSequence featureId, JsonPointer desiredPropertyPath,
            JsonValue desiredPropertyValue);

    /**
     * Removes all desired properties from the given Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature of which all desired properties are to be removed.
     * @return a copy of this Thing with all of the Feature's desired properties removed.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @since 1.5.0
     */
    Thing removeFeatureDesiredProperties(CharSequence featureId);

    /**
     * Removes the given desired property from a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the desired property to be removed.
     * @return a copy of this Thing with the given Features desired property removed.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    default Thing removeFeatureDesiredProperty(final CharSequence featureId, final CharSequence propertyPath) {
        return removeFeatureDesiredProperty(featureId, JsonPointer.of(propertyPath));
    }

    /**
     * Removes the given desired property from a Feature on a copy of this Thing.
     *
     * @param featureId the ID of the Feature.
     * @param propertyPath the hierarchical path within the Feature to the desired property to be removed.
     * @return a copy of this Thing with the given Feature desired property removed.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.5.0
     */
    Thing removeFeatureDesiredProperty(CharSequence featureId, JsonPointer propertyPath);

    /**
     * Returns the current lifecycle of this Thing.
     *
     * @return the current lifecycle of this Thing.
     */
    Optional<ThingLifecycle> getLifecycle();

    /**
     * Sets the given lifecycle to a copy of this Thing.
     *
     * @param newLifecycle the lifecycle to set.
     * @return a copy of this Thing with the lifecycle set to {@code newLifecycle}.
     * @throws NullPointerException if {@code newLifecycle} is {@code null}.
     */
    Thing setLifecycle(ThingLifecycle newLifecycle);

    /**
     * Indicates whether this Thing has the given lifecycle.
     *
     * @param lifecycle the lifecycle to be checked for.
     * @return {@code true} if this Thing has {@code lifecycle} as its lifecycle, {@code false} else.
     */
    default boolean hasLifecycle(final ThingLifecycle lifecycle) {
        return getLifecycle()
                .filter(actualLifecycle -> Objects.equals(actualLifecycle, lifecycle))
                .isPresent();
    }

    /**
     * Sets the given Policy ID on a copy of this Thing.
     *
     * @param policyId the Policy ID to set.
     * @return a copy of this Thing with {@code policyId} as its Policy ID.
     */
    Thing setPolicyId(@Nullable PolicyId policyId);

    /**
     * Returns the Policy ID of this Thing.
     *
     * @return the Policy ID of this Thing.
     */
    Optional<PolicyId> getPolicyId();

    /**
     * Returns the Features of this Thing.
     *
     * @return the Features of this Thing.
     */
    Optional<Features> getFeatures();

    /**
     * Sets the given Features to a copy of this Thing.
     *
     * @param features the Features to be set.
     * @return a copy of this Thing with the features set.
     */
    Thing setFeatures(@Nullable Features features);

    /**
     * Removes all Features from a copy of this Thing.
     *
     * @return a copy of this Thing with all of its Features removed.
     */
    Thing removeFeatures();

    /**
     * Sets the given Feature to a copy of this Thing. An already existing Feature with the same ID is replaced.
     *
     * @param feature the Feature to be set.
     * @return a copy of this Thing with the given feature.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    Thing setFeature(Feature feature);

    /**
     * Removes the Feature with the specified ID from a copy of this Thing.
     *
     * @param featureId the ID of the Feature to be removed.
     * @return a copy of this Thing without the Feature with the given ID.
     */
    Thing removeFeature(String featureId);

    /**
     * An enumeration of the known {@link JsonField}s of a Thing.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the Thing's lifecycle.
         */
        public static final JsonFieldDefinition<String> LIFECYCLE = JsonFactory.newStringFieldDefinition("__lifecycle",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's namespace.
         */
        public static final JsonFieldDefinition<String> NAMESPACE = JsonFactory.newStringFieldDefinition("_namespace",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("_revision",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's modified timestamp in ISO-8601 format.
         */
        public static final JsonFieldDefinition<String> MODIFIED = JsonFactory.newStringFieldDefinition("_modified",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's created timestamp in ISO-8601 format.
         *
         * @since 1.2.0
         */
        public static final JsonFieldDefinition<String> CREATED = JsonFactory.newStringFieldDefinition("_created",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's ID.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's Policy ID.
         */
        public static final JsonFieldDefinition<String> POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's definition.
         */
        public static final JsonFieldDefinition<JsonValue> DEFINITION =
                JsonFactory.newJsonValueFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's attributes.
         */
        public static final JsonFieldDefinition<JsonObject> ATTRIBUTES =
                JsonFactory.newJsonObjectFieldDefinition("attributes", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's features.
         */
        public static final JsonFieldDefinition<JsonObject> FEATURES =
                JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's metadata.
         *
         * @since 1.2.0
         */
        public static final JsonFieldDefinition<JsonObject> METADATA = JsonFactory.newJsonObjectFieldDefinition(
                "_metadata",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
