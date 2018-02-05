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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

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
public interface Thing extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * The regex pattern a Thing Namespace.
     */
    String NAMESPACE_PREFIX_REGEX = "(?<ns>|(?:(?:[a-zA-Z]\\w*)(?:\\.[a-zA-Z]\\w*)*))";

    /**
     * The regex pattern a Thing ID has to conform to. Defined by
     * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>.
     */
    String ID_NON_NAMESPACE_REGEX =
            "(?<id>|(?:[-\\w:@&=+,.!~*'_;]|%\\p{XDigit}{2})(?:[-\\w:@&=+,.!~*'$_;]|%\\p{XDigit}{2})*)";

    /**
     * The regex pattern a Thing ID has to conform to. Combines "namespace" pattern (java package notation + a
     * semicolon) and "non namespace" (Defined by <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>) pattern.
     */
    String ID_REGEX = NAMESPACE_PREFIX_REGEX + "\\:" + ID_NON_NAMESPACE_REGEX;

    /**
     * The set of permissions which at least must be present in the ACL of a Thing for one Authorization Subject.
     */
    @SuppressWarnings("squid:S2386")
    Permissions MIN_REQUIRED_PERMISSIONS =
            ThingsModelFactory.newUnmodifiablePermissions(Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);

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
        return (getAccessControlList().isPresent() && !getPolicyId().isPresent())
                ? JsonSchemaVersion.V_1 : JsonSchemaVersion.LATEST;
    }

    /**
     * Returns the ID of this Thing.
     *
     * @return the ID of this Thing.
     */
    Optional<String> getId();

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
     * Returns the current revision of this Thing.
     *
     * @return the current revision of this Thing.
     */
    Optional<ThingRevision> getRevision();

    /**
     * Returns the modified timestamp of this Thing.
     *
     * @return the timestamp.
     */
    Optional<Instant> getModified();

    /**
     * Returns the Access Control List of this Thing.
     *
     * @return the Access Control List of this Thing.
     */
    Optional<AccessControlList> getAccessControlList();

    /**
     * Sets the given Access Control List on a copy of this Thing. Removes any Policy and Policy ID from this Thing.
     *
     * @param accessControlList the Access Control List to be set.
     * @return a copy of this Thing with {@code accessControlList} as its ACL.
     */
    Thing setAccessControlList(AccessControlList accessControlList);

    /**
     * Sets the given ACL entry to the Access Control List of a copy of this Thing. An already existing entry with the
     * same authorization subject is overwritten.
     *
     * @param aclEntry the entry to be set.
     * @return a copy of this Thing with the changed ACL.
     * @throws NullPointerException if {@code aclEntry} is {@code null}.
     */
    Thing setAclEntry(AclEntry aclEntry);

    /**
     * Removes all permissions which are associated to the specified authorization subject in the Access Control List of
     * a copy of this Thing.
     *
     * @param authorizationSubject the authorization subject of which all permissions are to be removed.
     * @return a copy of this Thing whose ACL does not contain any entries which are associated with the specified
     * authorization subject.
     * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
     */
    Thing removeAllPermissionsOf(AuthorizationSubject authorizationSubject);

    /**
     * Returns the Policy ID of this Thing.
     *
     * @return the Policy ID of this Thing.
     */
    Optional<String> getPolicyId();

    /**
     * Sets the given Policy ID on a copy of this Thing.
     *
     * @param policyId the Policy ID to set.
     * @return a copy of this Thing with {@code policyId} as its Policy ID.
     */
    Thing setPolicyId(@Nullable String policyId);

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
     * Returns all non hidden marked fields of this object.
     *
     * @return a JSON object representation of this object including only non hidden marked fields.
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
     * An enumeration of the known {@link JsonField}s of a Thing.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's lifecycle.
         */
        public static final JsonFieldDefinition<String> LIFECYCLE =
                JsonFactory.newStringFieldDefinition("__lifecycle", FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's namespace.
         */
        public static final JsonFieldDefinition<String> NAMESPACE =
                JsonFactory.newStringFieldDefinition("_namespace", FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION =
                JsonFactory.newLongFieldDefinition("_revision", FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's modified timestamp in ISO-8601 format.
         */
        public static final JsonFieldDefinition<String> MODIFIED =
                JsonFactory.newStringFieldDefinition("_modified", FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's ID.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's Access Control List (ACL).
         */
        public static final JsonFieldDefinition<JsonObject> ACL =
                JsonFactory.newJsonObjectFieldDefinition("acl", FieldType.REGULAR, JsonSchemaVersion.V_1);

        /**
         * JSON field containing the Thing's Policy ID.
         */
        public static final JsonFieldDefinition<String> POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's attributes.
         */
        public static final JsonFieldDefinition<JsonObject> ATTRIBUTES =
                JsonFactory.newJsonObjectFieldDefinition("attributes", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Thing's features.
         */
        public static final JsonFieldDefinition<JsonObject> FEATURES =
                JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
