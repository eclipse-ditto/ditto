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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Factory that creates new {@code things} objects.
 */
@Immutable
public final class ThingsModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable empty {@link Attributes}.
     *
     * @return the new immutable empty {@code Attributes}.
     */
    public static Attributes emptyAttributes() {
        return AttributesModelFactory.emptyAttributes();
    }

    /**
     * Returns a new immutable {@link Attributes} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Attributes}.
     */
    public static Attributes nullAttributes() {
        return AttributesModelFactory.nullAttributes();
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code Attributes}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static Attributes newAttributes(final JsonObject jsonObject) {
        return AttributesModelFactory.newAttributes(jsonObject);
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code Attributes}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Attributes}.
     */
    public static Attributes newAttributes(final String jsonString) {
        return AttributesModelFactory.newAttributes(jsonString);
    }

    /**
     * Returns a new empty builder for a {@link Attributes}.
     *
     * @return the builder.
     */
    public static AttributesBuilder newAttributesBuilder() {
        return AttributesModelFactory.newAttributesBuilder();
    }

    /**
     * Returns a new builder for a {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static AttributesBuilder newAttributesBuilder(final JsonObject jsonObject) {
        return AttributesModelFactory.newAttributesBuilder(jsonObject);
    }

    /**
     * Returns an immutable instance of {@link FeatureDefinition.Identifier}.
     *
     * @param namespace the namespace of the returned Identifier.
     * @param name the name of the returned Identifier.
     * @param version the version of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public static FeatureDefinition.Identifier newFeatureDefinitionIdentifier(final CharSequence namespace,
            final CharSequence name, final CharSequence version) {

        return ImmutableFeatureDefinitionIdentifier.getInstance(namespace, name, version);
    }

    /**
     * Parses the specified CharSequence and returns an immutable instance of {@link FeatureDefinition.Identifier}.
     *
     * @param featureIdentifierAsCharSequence CharSequence-representation of a FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code featureIdentifierAsCharSequence} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code featureIdentifierAsCharSequence} is invalid.
     */
    public static FeatureDefinition.Identifier newFeatureDefinitionIdentifier(
            final CharSequence featureIdentifierAsCharSequence) {

        if (featureIdentifierAsCharSequence instanceof FeatureDefinition.Identifier) {
            return (FeatureDefinition.Identifier) featureIdentifierAsCharSequence;
        }
        return ImmutableFeatureDefinitionIdentifier.ofParsed(featureIdentifierAsCharSequence);
    }

    /**
     * Parses the specified JsonArray and returns an immutable instance of {@code FeatureDefinition} which is
     * initialised with the values of the given JSON array.
     *
     * @param jsonArray JSON array containing the identifiers of the FeatureDefinition to be returned. Non-string values
     * are ignored.
     * @return the instance.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws FeatureDefinitionEmptyException if {@code jsonArray} is empty.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier string of the array is invalid.
     */
    public static FeatureDefinition newFeatureDefinition(final JsonArray jsonArray) {
        checkNotNull(jsonArray, "JSON array");
        if (!jsonArray.isNull()) {
            return ImmutableFeatureDefinition.fromJson(jsonArray);
        }
        return nullFeatureDefinition();
    }

    /**
     * Returns a new immutable {@link FeatureDefinition} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonArray}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code FeatureDefinition}.
     * @throws FeatureDefinitionEmptyException if the JSON array is empty.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier of the JSON array is invalid.
     */
    public static FeatureDefinition newFeatureDefinition(final String jsonString) {
        final JsonArray jsonArray =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newArray(jsonString));

        return newFeatureDefinition(jsonArray);
    }

    /**
     * Returns a new immutable {@link FeatureDefinition} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code FeatureDefinition}.
     */
    public static FeatureDefinition nullFeatureDefinition() {
        return NullFeatureDefinition.getInstance();
    }

    /**
     * Parses the specified CharSequence and returns a mutable builder with a fluent API for an immutable {@code
     * FeatureDefinition}. The returned builder is initialised with the parsed Identifier as its first one.
     *
     * @param firstIdentifier CharSequence-representation of the first FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code firstIdentifier} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code firstIdentifier} is invalid.
     */
    public static FeatureDefinitionBuilder newFeatureDefinitionBuilder(final CharSequence firstIdentifier) {
        return ImmutableFeatureDefinition.getBuilder(newFeatureDefinitionIdentifier(firstIdentifier));
    }

    /**
     * Returns a new builder for an immutable {@link FeatureDefinition} which is initialised with the values of the
     * given JSON array.
     *
     * @param jsonArray provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier of the array is invalid.
     */
    public static FeatureDefinitionBuilder newFeatureDefinitionBuilder(final JsonArray jsonArray) {
        return ImmutableFeatureDefinition.Builder.getInstance().addAll(newFeatureDefinition(jsonArray));
    }

    /**
     * Returns a new immutable empty {@link FeatureProperties}.
     *
     * @return the new immutable empty {@code FeatureProperties}.
     */
    public static FeatureProperties emptyFeatureProperties() {
        return ImmutableFeatureProperties.empty();
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code FeatureProperties}.
     */
    public static FeatureProperties nullFeatureProperties() {
        return NullFeatureProperties.newInstance();
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code FeatureProperties}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FeatureProperties newFeatureProperties(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object for initialization");

        if (!jsonObject.isNull()) {
            return ImmutableFeatureProperties.of(jsonObject);
        } else {
            return nullFeatureProperties();
        }
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureProperties}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code FeatureProperties}.
     */
    public static FeatureProperties newFeatureProperties(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatureProperties(jsonObject);
    }

    /**
     * Returns a new empty builder for an immutable {@link FeatureProperties}.
     *
     * @return the builder.
     */
    public static FeaturePropertiesBuilder newFeaturePropertiesBuilder() {
        return ImmutableFeaturePropertiesBuilder.empty();
    }

    /**
     * Returns a new builder for an immutable {@link FeatureProperties} which is initialised with the values of the
     * given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FeaturePropertiesBuilder newFeaturePropertiesBuilder(final JsonObject jsonObject) {
        return ImmutableFeaturePropertiesBuilder.of(jsonObject);
    }

    /**
     * Returns a new immutable {@link Feature} which represents {@code null}.
     *
     * @param featureId the ID of the new Feature.
     * @return the new {@code null}-like {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static Feature nullFeature(final String featureId) {
        return NullFeature.of(featureId);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID.
     *
     * @param featureId the ID of the new Feature.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static Feature newFeature(final String featureId) {
        return ImmutableFeature.of(featureId);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID and properties.
     *
     * @param featureId the ID of the new feature.
     * @param featureProperties the properties of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureProperties featureProperties) {
        return ImmutableFeature.of(featureId, featureProperties);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID, properties and Definition.
     *
     * @param featureId the ID of the new feature.
     * @param featureDefinition the Definition of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureDefinition featureDefinition) {
        return ImmutableFeature.of(featureId, featureDefinition, null);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID, properties and Definition.
     *
     * @param featureId the ID of the new feature.
     * @param featureDefinition the Definition of the new Feature or {@code null}.
     * @param featureProperties the properties of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureDefinition featureDefinition,
            @Nullable final FeatureProperties featureProperties) {

        return ImmutableFeature.of(featureId, featureDefinition, featureProperties);
    }

    /**
     * Returns a new builder for an immutable {@link Feature} from scratch with a fluent API.
     *
     * @return the builder.
     */
    public static FeatureBuilder.FromScratchBuildable newFeatureBuilder() {
        return ImmutableFeatureFromScratchBuilder.newFeatureFromScratch();
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given Feature.
     *
     * @param feature provides the initial values for the result.
     * @return the builder.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    public static FeatureBuilder.FromCopyBuildable newFeatureBuilder(final Feature feature) {
        return ImmutableFeatureFromCopyBuilder.of(feature);
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given JSON
     * object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FeatureBuilder.FromJsonBuildable newFeatureBuilder(final JsonObject jsonObject) {
        return ImmutableFeatureFromScratchBuilder.newFeatureFromJson(jsonObject);
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given JSON
     * string. The JSON string is parsed in a fault tolerant way. I. e. all properties which cannot be deserialized are
     * supposed to not exist.
     *
     * @param jsonString string the JSON string representation of a Feature.
     * @return the builder.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Feature}.
     * @see #newFeatureBuilder(JsonObject)
     */
    public static FeatureBuilder.FromJsonBuildable newFeatureBuilder(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatureBuilder(jsonObject);
    }

    /**
     * Returns a new immutable empty {@link Features}.
     *
     * @return the new immutable empty {@code Features}.
     */
    public static Features emptyFeatures() {
        return ImmutableFeatures.empty();
    }

    /**
     * Returns a new immutable {@link Features} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Features}.
     */
    public static Features nullFeatures() {
        return NullFeatures.newInstance();
    }

    /**
     * Returns a new immutable {@link Features} which is initialised with the features of the given Iterable.
     *
     * @param features the features to initialise the result with.
     * @return the new immutable {@code Features} which is initialised with {@code features}.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static Features newFeatures(final Iterable<Feature> features) {
        return ImmutableFeatures.of(features);
    }

    /**
     * Returns a new immutable {@link Features} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new immutable {@code Features} which is initialised by the data of {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if any JSON field which is supposed to represent a Feature is not a JSON object.
     */
    public static Features newFeatures(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "Features JSON object");

        final Features result;

        if (!jsonObject.isNull()) {
            final Set<Feature> features = jsonObject.stream()
                    .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                    .peek(field -> {
                        if (!(field.getValue().isObject())) {
                            final String errorMsgTemplate =
                                    "The Feature value is not an object for Feature with ID ''{0}'': {1}";
                            final String errorMsg =
                                    MessageFormat.format(errorMsgTemplate, field.getKey(), field.getValue());
                            throw new DittoJsonException(new JsonParseException(errorMsg));
                        }
                    })
                    .map(field -> ImmutableFeatureFromScratchBuilder.newFeatureFromJson(field.getValue().asObject())
                            .useId(field.getKeyName())
                            .build())
                    .collect(Collectors.toSet());

            result = ImmutableFeatures.of(features);
        } else {
            result = nullFeatures();
        }

        return result;
    }

    /**
     * Returns a new immutable {@link Features} based on the given JSON string.
     *
     * @param jsonString provides the initial values of the result.
     * @return the new immutable initialised {@code Features}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Features}.
     */
    public static Features newFeatures(final String jsonString) {
        final JsonObject featuresJsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatures(featuresJsonObject);
    }

    /**
     * Returns a new immutable {@link Features} which is initialised with the given features.
     *
     * @param feature the initial Feature of the result.
     * @param additionalFeatures additional features of the result.
     * @return the new immutable {@code Features} which is initialised with {@code feature} and potentially with {@code
     * additionalFeatures}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Features newFeatures(final Feature feature, final Feature... additionalFeatures) {
        return ImmutableFeatures.of(feature, additionalFeatures);
    }

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}.
     *
     * @return the builder.
     */
    public static FeaturesBuilder newFeaturesBuilder() {
        return ImmutableFeaturesBuilder.newInstance();
    }

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}. The builder is initialised
     * with the given features.
     *
     * @param features the initial features of the new builder.
     * @return the builder.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static FeaturesBuilder newFeaturesBuilder(final Iterable<Feature> features) {
        final FeaturesBuilder result = ImmutableFeaturesBuilder.newInstance();
        result.setAll(features);
        return result;
    }

    /**
     * Returns a new immutable {@link ThingRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code ThingRevision}.
     */
    public static ThingRevision newThingRevision(final long revisionNumber) {
        return ImmutableThingRevision.of(revisionNumber);
    }

    /**
     * Returns a new empty <em>mutable</em> {@link Permissions}.
     *
     * @return the new {@code Permissions}.
     */
    public static Permissions noPermissions() {
        return AccessControlListModelFactory.noPermissions();
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing all available permissions.
     *
     * @return the new {@code Permissions}.
     * @see Permission#values()
     */
    public static Permissions allPermissions() {
        return AccessControlListModelFactory.allPermissions();
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing the given permissions.
     *
     * @param permissions the permissions to initialise the result with.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null};
     */
    public static Permissions newPermissions(final Collection<Permission> permissions) {
        return AccessControlListModelFactory.newPermissions(permissions);
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions newPermissions(final Permission permission, final Permission... furtherPermissions) {
        return AccessControlListModelFactory.newPermissions(permission, furtherPermissions);
    }

    /**
     * Returns a new unmodifiable {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions newUnmodifiablePermissions(final Permission permission,
            final Permission... furtherPermissions) {
        return AccessControlListModelFactory.newUnmodifiablePermissions(permission, furtherPermissions);
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permission the permission of the new ACL entry.
     * @param furtherPermissions additional permission of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry newAclEntry(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        return AccessControlListModelFactory.newAclEntry(authorizationSubject, permission, furtherPermissions);
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permissions the permissions of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry newAclEntry(final AuthorizationSubject authorizationSubject,
            final Iterable<Permission> permissions) {
        return AccessControlListModelFactory.newAclEntry(authorizationSubject, permissions);
    }

    /**
     * Returns a new immutable {@link AclEntry} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new ACL entry.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static AclEntry newAclEntry(final JsonObject jsonObject) {
        return AccessControlListModelFactory.newAclEntry(jsonObject);
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject ID and the given JSON value
     * which provides the permissions.
     *
     * @param authorizationSubjectId the ID of the authorization subject of the new ACL entry.
     * @param permissionsValue a JSON value which represents the permissions of the authorization subject of the new ACL
     * entry. This value is supposed to be a JSON object.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws DittoJsonException if {@code permissionsValue} is not a JSON object.
     * @throws AclEntryInvalidException if {@code permissionsValue} does not contain a
     * {@code boolean} value for the required permissions.
     */
    public static AclEntry newAclEntry(final CharSequence authorizationSubjectId, final JsonValue permissionsValue) {
        return AccessControlListModelFactory.newAclEntry(authorizationSubjectId, permissionsValue);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}.
     *
     * @return the new builder.
     */
    public static AccessControlListBuilder newAclBuilder() {
        return AccessControlListModelFactory.newAclBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}. The builder is
     * initialised with the given ACL entries.
     *
     * @param aclEntries the initial entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code aclEntries} is {@code null}.
     */
    public static AccessControlListBuilder newAclBuilder(final Iterable<AclEntry> aclEntries) {
        return AccessControlListModelFactory.newAclBuilder(aclEntries);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}. The builder is
     * initialised with the given ACL entries.
     *
     * @param aclEntries the initial entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code aclEntries} is {@code null}.
     */
    public static AccessControlListBuilder newAclBuilder(final Optional<? extends Iterable<AclEntry>> aclEntries) {
        return AccessControlListModelFactory.newAclBuilder(aclEntries);
    }

    /**
     * Returns a new empty immutable {@link AccessControlList}.
     *
     * @return the new ACL.
     */
    public static AccessControlList emptyAcl() {
        return AccessControlListModelFactory.emptyAcl();
    }

    /**
     * Returns a new immutable Access Control List (ACL) which is initialised with the specified entries.
     *
     * @param entry the mandatory entry of the ACL.
     * @param furtherEntries additional entries of the ACL.
     * @return the new initialised Access Control List.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AccessControlList newAcl(final AclEntry entry, final AclEntry... furtherEntries) {
        return AccessControlListModelFactory.newAcl(entry, furtherEntries);
    }

    /**
     * Returns a new immutable Access Control List (ACL) which is initialised with the specified entries.
     *
     * @param entries the entries of the ACL.
     * @return the new initialised Access Control List.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AccessControlList newAcl(final Iterable<AclEntry> entries) {
        return AccessControlListModelFactory.newAcl(entries);
    }

    /**
     * Returns a new immutable Access Control List (ACL) based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of an ACL.
     * @return the new initialised {@code AccessControlList}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed to {@link AccessControlList}.
     */
    public static AccessControlList newAcl(final JsonObject jsonObject) {
        return AccessControlListModelFactory.newAcl(jsonObject);
    }

    /**
     * Returns a new immutable Access Control List (ACL) based on the given JSON string.
     *
     * @param jsonString the JSON object representation of an ACL.
     * @return the new initialised {@code AccessControlList}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@link AccessControlList}.
     */
    public static AccessControlList newAcl(final String jsonString) {
        return AccessControlListModelFactory.newAcl(jsonString);
    }

    /**
     * Returns a new immutable {@link Thing} based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of a Thing.
     * @return the new Thing.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed to {@code Thing}.
     */
    public static Thing newThing(final JsonObject jsonObject) {
        return newThingBuilder(jsonObject).build();
    }

    /**
     * Returns a new immutable {@link Thing} based on the given JSON string.
     *
     * @param jsonString the JSON string representation of a Thing.
     * @return the new Thing.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Thing}.
     */
    public static Thing newThing(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newThingBuilder(jsonObject).build();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} from scratch.
     *
     * @return the new builder.
     */
    public static ThingBuilder.FromScratch newThingBuilder() {
        return ImmutableThingFromScratchBuilder.newInstance();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} based on the given JSON object. The
     * JSON object is parsed in a fault tolerant way. I. e. all properties which cannot be deserialized are supposed to
     * not exist.
     *
     * @param jsonObject the JSON object representation of a Thing.
     * @return the new builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed to {@code Thing}.
     */
    public static ThingBuilder.FromCopy newThingBuilder(final JsonObject jsonObject) {
        return DittoJsonException.wrapJsonRuntimeException(() -> ImmutableThingFromCopyBuilder.of(jsonObject));
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} based on the given JSON string. The
     * JSON string is parsed in a fault tolerant way. I. e. all properties which cannot be deserialized are supposed to
     * not exist.
     *
     * @param jsonString string the JSON string representation of a Thing.
     * @return the new builder.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Thing}.
     * @see #newThingBuilder(JsonObject)
     */
    public static ThingBuilder.FromCopy newThingBuilder(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newThingBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing}. The builder is initialised with the
     * properties of the given Thing.
     *
     * @param thing the Thing which provides the initial properties of the builder.
     * @return the new builder.
     * @throws NullPointerException if {@code thing} is {@code null}.
     */
    public static ThingBuilder.FromCopy newThingBuilder(final Thing thing) {
        return ImmutableThingFromCopyBuilder.of(thing);
    }

}
