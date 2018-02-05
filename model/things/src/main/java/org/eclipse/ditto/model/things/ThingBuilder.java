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
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Builder for instances of {@link Thing} which uses Object Scoping and Method Chaining to provide a convenient usage
 * experience.
 */
public interface ThingBuilder {

    /**
     * Generates a random Thing ID with an empty namespace.
     *
     * @return the ID
     */
    static String generateRandomThingId() {
        // for now: use "empty" namespace for generated IDs:
        return ":" + UUID.randomUUID();
        // this will change into using the "default" namespace of a solution instead later on
    }

    /**
     * A mutable builder with a fluent API for an immutable {@link Thing} from scratch.
     *
     */
    @NotThreadSafe
    interface FromScratch {

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permission the permission of the authorization subject to be set.
         * @param furtherPermissions additional permissions of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setPermissions(AuthorizationSubject authorizationSubject, Permission permission,
                Permission... furtherPermissions);

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permissions the permission of of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setPermissions(AuthorizationSubject authorizationSubject, Permissions permissions);

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param aclEntries the entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code aclEntries} is {@code null}.
         */
        FromScratch setPermissions(Iterable<AclEntry> aclEntries);

        /**
         * Sets permissions to this builder. The permissions are parsed from the JSON object representation of an Access
         * Control List. All previously set ACL entries with the same authorization subject are replaced.
         *
         * @param accessControlListJsonObject the JSON object representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code accessControlListJsonObject} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonObject} cannot be parsed
         * to {@link AccessControlList}.
         */
        FromScratch setPermissions(JsonObject accessControlListJsonObject);

        /**
         * Sets permissions to this builder. The permissions are parsed from the JSON string representation of an Access
         * Control List. All previously set ACL entries with the same authorization subject are replaced.
         *
         * @param accessControlListJsonString the JSON string representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonString} cannot be parsed
         * to {@link AccessControlList}.
         * @see #setPermissions(JsonObject)
         */
        FromScratch setPermissions(String accessControlListJsonString);

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param aclEntry the ACL entry of the be set.
         * @param furtherAclEntries additional ACL entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setPermissions(AclEntry aclEntry, AclEntry... furtherAclEntries);

        /**
         * Removes all permissions of the given authorization subject from this builder.
         *
         * @param authorizationSubject the authorization subject of which all permissions are to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
         */
        FromScratch removePermissionsOf(AuthorizationSubject authorizationSubject);

        /**
         * Removes all permissions from this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch removeAllPermissions();

        /**
         * Sets the given Policy ID to this builder.
         *
         * @param policyId the Policy ID to set.
         * @return this builder to allow method chaining.
         */
        FromScratch setPolicyId(@Nullable String policyId);

        /**
         * Removes the Policy ID from this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch removePolicyId();

        /**
         * Sets the given attributes to this builder.
         *
         * @param attributes the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code attributes} is {@code null}.
         */
        FromScratch setAttributes(Attributes attributes);

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON object representation of
         * {@link Attributes}.
         *
         * @param attributesJsonObject the JSON object representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code attributesJsonObject} is {@code null}.
         */
        FromScratch setAttributes(JsonObject attributesJsonObject);

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON string representation of
         * {@link Attributes}.
         *
         * @param attributesJsonString JSON string representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code attributesJsonString} is not a valid JSON
         * object.
         */
        FromScratch setAttributes(String attributesJsonString);

        /**
         * Removes all attributes from this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch removeAllAttributes();

        /**
         * Sets empty attributes to this builder. All already set attributes are discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch setEmptyAttributes();

        /**
         * Sets attributes to this builder which represent semantically {@code null}. All already set attributes are
         * discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch setNullAttributes();

        /**
         * Sets the given attribute to this builder.
         *
         * @param attributePath the hierarchical path to the attribute value.
         * @param attributeValue the value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code attributePath} is empty.
         */
        FromScratch setAttribute(JsonPointer attributePath, JsonValue attributeValue);

        /**
         * Removes the attribute at the given path from this builder.
         *
         * @param attributePath the hierarchical path to the attribute to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if attributes were set already and {@code attributePath} is {@code null}.
         */
        FromScratch removeAttribute(JsonPointer attributePath);

        /**
         * Sets the given Feature to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param feature the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code feature} is {@code null}.
         */
        FromScratch setFeature(Feature feature);

        /**
         * Sets a Feature with the given ID to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param featureId the identifier of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch setFeature(String featureId);

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch setFeature(String featureId, FeatureDefinition featureDefinition,
                FeatureProperties featureProperties);

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch setFeature(String featureId, FeatureProperties featureProperties);

        /**
         * Removes the Feature with the given ID from this builder. If this was the last Feature the Thing will
         * not have features.
         *
         * @param featureId the ID of the Feature to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch removeFeature(String featureId);

        /**
         * Sets the given definition to the Feature with the given ID on this builder. If this builder does not yet
         * know a Feature with the given ID it creates one.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setFeatureDefinition(String featureId, FeatureDefinition featureDefinition);

        /**
         * Removes the definition from Feature with the given identifier on this builder.
         *
         * @param featureId the ID of the Feature from which the definition is to be deleted.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch removeFeatureDefinition(String featureId);

        /**
         * Sets the given property to the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path within the Feature to the property to be set.
         * @param propertyValue the property value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setFeatureProperty(String featureId, JsonPointer propertyPath, JsonValue propertyValue);

        /**
         * Removes the given property from the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path to within the Feature to the property to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch removeFeatureProperty(String featureId, JsonPointer propertyPath);

        /**
         * Sets the given properties to the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param featureProperties the properties to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromScratch setFeatureProperties(String featureId, FeatureProperties featureProperties);

        /**
         * Removes all properties from the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromScratch removeFeatureProperties(String featureId);

        /**
         * Sets the features to this builder. The features are parsed from the given JSON object representation of
         * {@link Features}.
         *
         * @param featuresJsonObject JSON object representation of the features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featuresJsonObject} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonObject} cannot be parsed to
         * {@link Features}.
         */
        FromScratch setFeatures(JsonObject featuresJsonObject);

        /**
         * Sets the Features of the Thing based on the given JSON object.
         *
         * @param featuresJsonString JSON string providing the Features of the Thing.
         * @return this builder to allow method chaining.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonString} cannot be parsed to
         * {@link Features}.
         */
        FromScratch setFeatures(String featuresJsonString);

        /**
         * Sets the given Features to this builder.
         *
         * @param features the Features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code features} is {@code null}.
         */
        FromScratch setFeatures(Iterable<Feature> features);

        /**
         * Removes all features from this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch removeAllFeatures();

        /**
         * Sets empty features to this builder. All already set features are discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch setEmptyFeatures();

        /**
         * Sets features to this builder which represents semantically {@code null}. All already set features are
         * discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch setNullFeatures();

        /**
         * Sets the given lifecycle to this builder.
         *
         * @param lifecycle the lifecycle to be set.
         * @return this builder to allow method chaining.
         */
        FromScratch setLifecycle(ThingLifecycle lifecycle);

        /**
         * Sets the given revision to this builder.
         *
         * @param revision the revision to be set.
         * @return this builder to allow method chaining.
         */
        FromScratch setRevision(ThingRevision revision);

        /**
         * Sets the given revision number to this builder.
         *
         * @param revisionNumber the revision number to be set.
         * @return this builder to allow method chaining.
         */
        FromScratch setRevision(long revisionNumber);

        /**
         * Sets the given modified to this builder.
         *
         * @param modified the modified to be set.
         * @return this builder to allow method chaining.
         */
        FromScratch setModified(Instant modified);

        /**
         * Sets the given Thing ID to this builder. The ID is required to include the Thing's namespace.
         *
         * @param thingId the Thing ID to be set.
         * @return this builder to allow method chaining.
         * @throws ThingIdInvalidException if {@code thingId} does not comply to the required pattern.
         */
        FromScratch setId(@Nullable String thingId);

        /**
         * Sets a generated Thing ID to this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromScratch setGeneratedId();

        /**
         * Creates a new immutable {@link Thing} containing all properties which were set to this builder beforehand.
         *
         * @return the new Thing.
         */
        Thing build();
    }

    /**
     * A mutable builder with a fluent API for an immutable {@link Thing}. This builder is initialised with the
     * properties of an existing Thing.
     *
     */
    @NotThreadSafe
    interface FromCopy {

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permission the permission of the authorization subject to be set.
         * @param furtherPermissions additional permissions of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setPermissions(final AuthorizationSubject authorizationSubject, final Permission permission,
                final Permission... furtherPermissions) {
            return setPermissions(existingAcl -> true, authorizationSubject, permission, furtherPermissions);
        }

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permission the permission of the authorization subject to be set.
         * @param furtherPermissions additional permissions of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate,
                AuthorizationSubject authorizationSubject, Permission permission, Permission... furtherPermissions);

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permissions the permission of of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setPermissions(final AuthorizationSubject authorizationSubject,
                final Permissions permissions) {

            return setPermissions(existingAcl -> true, authorizationSubject, permissions);
        }

        /**
         * Sets the given permissions to this builder. Previously set permissions for the same authorization subject are
         * replaced.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param authorizationSubject the authorization subject to set the permissions for.
         * @param permissions the permission of of the authorization subject to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate,
                AuthorizationSubject authorizationSubject, Permissions permissions);

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param aclEntries the entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code aclEntries} is {@code null}.
         */
        default FromCopy setPermissions(final Iterable<AclEntry> aclEntries) {
            return setPermissions(existingAcl -> true, aclEntries);
        }

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param aclEntries the entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate, Iterable<AclEntry> aclEntries);

        /**
         * Sets permissions to this builder. The permissions are parsed from the JSON object representation of an Access
         * Control List. All previously set ACL entries with the same authorization subject are replaced.
         *
         * @param accessControlListJsonObject the JSON object representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code accessControlListJsonObject} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonObject} cannot be parsed
         * to {@link AccessControlList}.
         */
        default FromCopy setPermissions(final JsonObject accessControlListJsonObject) {
            return setPermissions(existingAcl -> true, accessControlListJsonObject);
        }

        /**
         * Sets permissions to this builder. The permissions are parsed from the JSON object representation of an Access
         * Control List. All previously set ACL entries with the same authorization subject are replaced.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param accessControlListJsonObject the JSON object representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonObject} cannot be parsed
         * to {@link AccessControlList}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate,
                JsonObject accessControlListJsonObject);

        /**
         * Sets the permissions of the Thing based on the given JSON representation of an Access Control List.
         *
         * @param accessControlListJsonString the JSON string representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonString} cannot be parsed
         * to {@link AccessControlList}.
         */
        default FromCopy setPermissions(final String accessControlListJsonString) {
            return setPermissions(existingAcl -> true, accessControlListJsonString);
        }

        /**
         * Sets the permissions of the Thing based on the given JSON representation of an Access Control List.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param accessControlListJsonString the JSON string representation of an Access Control List.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingAclPredicate} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code accessControlListJsonString} cannot be parsed
         * to {@link AccessControlList}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate, String accessControlListJsonString);

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param aclEntry the ACL entry of the be set.
         * @param furtherAclEntries additional ACL entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setPermissions(final AclEntry aclEntry, final AclEntry... furtherAclEntries) {
            return setPermissions(existingAcl -> true, aclEntry, furtherAclEntries);
        }

        /**
         * Sets the given permissions to this builder. All previously set ACL entries with the same authorization
         * subject are replaced.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param aclEntry the ACL entry of the be set.
         * @param furtherAclEntries additional ACL entries to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setPermissions(Predicate<AccessControlList> existingAclPredicate, AclEntry aclEntry,
                AclEntry... furtherAclEntries);

        /**
         * Removes all permissions of the given authorization subject from the Thing.
         *
         * @param authorizationSubject the authorization subject of which all permissions are removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
         */
        default FromCopy removePermissionsOf(final AuthorizationSubject authorizationSubject) {
            return removePermissionsOf(existingAcl -> true, authorizationSubject);
        }

        /**
         * Removes all permissions of the given authorization subject from the Thing.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @param authorizationSubject the authorization subject of which all permissions are removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy removePermissionsOf(Predicate<AccessControlList> existingAclPredicate,
                AuthorizationSubject authorizationSubject);

        /**
         * Removes all permissions from this builder.
         *
         * @return this builder to allow method chaining.
         */
        default FromCopy removeAllPermissions() {
            return removeAllPermissions(existingAcl -> true);
        }

        /**
         * Removes all permissions from this builder.
         *
         * @param existingAclPredicate a predicate to decide whether the given permissions are set. The predicate
         * receives the ACL which consists of the currently set permissions.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingAclPredicate} is {@code null}.
         */
        FromCopy removeAllPermissions(Predicate<AccessControlList> existingAclPredicate);

        /**
         * Sets the given Policy ID to this builder.
         *
         * @param policyId the Policy ID to set.
         * @return this builder to allow method chaining.
         */
        FromCopy setPolicyId(@Nullable String policyId);

        /**
         * Removes the Policy ID from this builder.
         *
         * @return this builder to allow method chaining.
         */
        FromCopy removePolicyId();

        /**
         * Sets the given attributes to this builder.
         *
         * @param attributes the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code attributes} is {@code null}.
         */
        default FromCopy setAttributes(final Attributes attributes) {
            return setAttributes(existingAttributes -> true, attributes);
        }

        /**
         * Sets the given attributes to this builder.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @param attributes the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setAttributes(Predicate<Attributes> existingAttributesPredicate, Attributes attributes);

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON object representation of
         * {@link Attributes}.
         *
         * @param attributesJsonObject the JSON object representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code attributesJsonObject} is {@code null}.
         */
        default FromCopy setAttributes(final JsonObject attributesJsonObject) {
            return setAttributes(existingAttributes -> true, attributesJsonObject);
        }

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON object representation of
         * {@link Attributes}.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @param attributesJsonObject the JSON object representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setAttributes(Predicate<Attributes> existingAttributesPredicate, JsonObject attributesJsonObject);

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON string representation of
         * {@link Attributes}.
         *
         * @param attributesJsonString JSON string representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code attributesJsonString} is not a valid JSON
         * object.
         */
        default FromCopy setAttributes(final String attributesJsonString) {
            return setAttributes(existingAttributes -> true, attributesJsonString);
        }

        /**
         * Sets the attributes to this builder. The attributes are parsed from the given JSON string representation of
         * {@link Attributes}.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @param attributesJsonString JSON string representation of the attributes to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingAttributesPredicate} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code attributesJsonString} is not a valid JSON
         * object.
         */
        FromCopy setAttributes(Predicate<Attributes> existingAttributesPredicate, String attributesJsonString);

        /**
         * Removes all attributes from this builder.
         *
         * @return this builder to allow method chaining.
         */
        default FromCopy removeAllAttributes() {
            return removeAllAttributes(existingAttributes -> true);
        }

        /**
         * Removes all attributes from this builder.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingAttributesPredicate} is {@code null}.
         */
        FromCopy removeAllAttributes(Predicate<Attributes> existingAttributesPredicate);

        /**
         * Sets attributes to this builder which represent semantically {@code null}. All already set attributes are
         * discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromCopy setNullAttributes();

        /**
         * Sets the given attribute to this builder.
         *
         * @param attributePath the hierarchical path to the attribute value.
         * @param attributeValue the value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code attributePath} is empty.
         */
        default FromCopy setAttribute(final JsonPointer attributePath, final JsonValue attributeValue) {
            return setAttribute(existingAttributes -> true, attributePath, attributeValue);
        }

        /**
         * Sets the given attribute to this builder.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @param attributePath the hierarchical path to the attribute value.
         * @param attributeValue the value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code attributePath} is empty.
         */
        FromCopy setAttribute(Predicate<Attributes> existingAttributesPredicate, JsonPointer attributePath,
                JsonValue attributeValue);

        /**
         * Removes the attribute at the given path from this builder.
         *
         * @param attributePath the hierarchical path to the attribute to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code attributePath} is {@code null}.
         */
        default FromCopy removeAttribute(final JsonPointer attributePath) {
            return removeAttribute(existingAttributes -> true, attributePath);
        }

        /**
         * Removes the attribute at the given path from this builder.
         *
         * @param existingAttributesPredicate a predicate to decide whether the given attributes are set. The predicate
         * receives the currently set attributes.
         * @param attributePath the hierarchical path to the attribute to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy removeAttribute(Predicate<Attributes> existingAttributesPredicate, JsonPointer attributePath);

        /**
         * Sets the given Feature to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param feature the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code feature} is {@code null}.
         */
        default FromCopy setFeature(final Feature feature) {
            return setFeature(existingFeatures -> true, feature);
        }

        /**
         * Sets the given Feature to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param feature the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeature(Predicate<Features> existingFeaturesPredicate, Feature feature);

        /**
         * Sets a Feature with the given ID to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param featureId the ID of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        default FromCopy setFeature(final String featureId) {
            return setFeature(existingFeatures -> true, featureId);
        }

        /**
         * Sets a Feature with the given ID to this builder. A previously set Feature with the same ID is replaced.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeature(Predicate<Features> existingFeaturesPredicate, String featureId);

        /**
         * Removes the Feature with the given ID from this builder.
         *
         * @param featureId the ID of the Feature to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        default FromCopy removeFeature(final String featureId) {
            return removeFeature(existingFeatures -> true, featureId);
        }

        /**
         * Removes the Feature with the given ID from this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy removeFeature(Predicate<Features> existingFeaturesPredicate, String featureId);

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        default FromCopy setFeature(final String featureId, final FeatureProperties featureProperties) {
            return setFeature(existingFeatures -> true, featureId, featureProperties);
        }

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeature(Predicate<Features> existingFeaturesPredicate, String featureId,
                FeatureProperties featureProperties);

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        default FromCopy setFeature(final String featureId, final FeatureDefinition featureDefinition,
                final FeatureProperties featureProperties) {

            return setFeature(existingFeatures -> true, featureId, featureDefinition, featureProperties);
        }

        /**
         * Sets a Feature with the given ID and properties to this builder. A previously set Feature with the
         * same ID is replaced.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @param featureProperties the properties of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeature(Predicate<Features> existingFeaturesPredicate,
                String featureId,
                FeatureDefinition featureDefinition,
                FeatureProperties featureProperties);

        /**
         * Sets the given definition to the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setFeatureDefinition(final String featureId, final FeatureDefinition featureDefinition) {
            return setFeatureDefinition(features -> true, featureId, featureDefinition);
        }

        /**
         * Sets the given definition to the Feature with the given ID on this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given definition is set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature to be set.
         * @param featureDefinition the definition of the Feature to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeatureDefinition(Predicate<Features> existingFeaturesPredicate, String featureId,
                FeatureDefinition featureDefinition);

        /**
         * Removes the definition from Feature with the given identifier on this builder.
         *
         * @param featureId the ID of the Feature from which the definition is to be deleted.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        FromCopy removeFeatureDefinition(String featureId);

        /**
         * Sets the given property to the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path within the Feature to the property to be set.
         * @param propertyValue the property value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setFeatureProperty(final String featureId, final JsonPointer propertyPath,
                final JsonValue propertyValue) {

            return setFeatureProperty(existingFeatures -> true, featureId, propertyPath, propertyValue);
        }

        /**
         * Sets the given property to the Feature with the given ID on this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path within the Feature to the property to be set.
         * @param propertyValue the property value to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeatureProperty(Predicate<Features> existingFeaturesPredicate, String featureId,
                JsonPointer propertyPath, JsonValue propertyValue);

        /**
         * Removes the given property from the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path to within the Feature to the property to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy removeFeatureProperty(final String featureId, final JsonPointer propertyPath) {
            return removeFeatureProperty(existingFeatures -> true, featureId, propertyPath);
        }

        /**
         * Removes the given property from the Feature with the given ID on this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature.
         * @param propertyPath the hierarchical path to within the Feature to the property to be removed.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy removeFeatureProperty(Predicate<Features> existingFeaturesPredicate, String featureId,
                JsonPointer propertyPath);

        /**
         * Sets the given properties to the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @param featureProperties the properties to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default FromCopy setFeatureProperties(final String featureId, final FeatureProperties featureProperties) {
            return setFeatureProperties(features -> true, featureId, featureProperties);
        }

        /**
         * Sets the given properties to the Feature with the given ID on this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature.
         * @param featureProperties the properties to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeatureProperties(Predicate<Features> existingFeaturesPredicate, String featureId,
                FeatureProperties featureProperties);

        /**
         * Removes all properties from the Feature with the given ID on this builder.
         *
         * @param featureId the ID of the Feature.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         */
        default FromCopy removeFeatureProperties(final String featureId) {
            return removeFeatureProperties(existingFeatures -> true, featureId);
        }

        /**
         * Removes all properties from the Feature with the given ID on this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featureId the ID of the Feature.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy removeFeatureProperties(Predicate<Features> existingFeaturesPredicate, String featureId);

        /**
         * Sets the features to this builder. The features are parsed from the given JSON object representation of
         * {@link Features}.
         *
         * @param featuresJsonObject JSON object representation of the features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featuresJsonObject} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonObject} cannot be
         * parsed to {@link Features}.
         */
        default FromCopy setFeatures(final JsonObject featuresJsonObject) {
            return setFeatures(existingFeatures -> true, featuresJsonObject);
        }

        /**
         * Sets the features to this builder. The features are parsed from the given JSON object representation of
         * {@link Features}.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featuresJsonObject JSON object representation of the features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonObject} cannot be
         * parsed to {@link Features}.
         */
        FromCopy setFeatures(Predicate<Features> existingFeaturesPredicate, JsonObject featuresJsonObject);

        /**
         * Sets the Features of the Thing based on the given JSON object.
         *
         * @param featuresJsonString JSON string providing the Features of the Thing.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featuresJsonString} is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonString} cannot be
         * parsed to {@link Features}.
         */
        default FromCopy setFeatures(final String featuresJsonString) {
            return setFeatures(existingFeatures -> true, featuresJsonString);
        }

        /**
         * Sets the Features of the Thing based on the given JSON object.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param featuresJsonString JSON string providing the Features of the Thing.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code featuresJsonString} cannot be
         * parsed to {@link Features}.
         */
        FromCopy setFeatures(Predicate<Features> existingFeaturesPredicate, String featuresJsonString);

        /**
         * Sets the given Features to this builder.
         *
         * @param features the Features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code features} is {@code null}.
         */
        default FromCopy setFeatures(final Iterable<Feature> features) {
            return setFeatures(existingFeatures -> true, features);
        }

        /**
         * Sets the given Features to this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @param features the Features to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        FromCopy setFeatures(Predicate<Features> existingFeaturesPredicate, Iterable<Feature> features);

        /**
         * Removes all features from this builder.
         *
         * @return this builder to allow method chaining.
         */
        default FromCopy removeAllFeatures() {
            return removeAllFeatures(existingFeatures -> true);
        }

        /**
         * Removes all features from this builder.
         *
         * @param existingFeaturesPredicate a predicate to decide whether the given features are set. The predicate
         * receives the currently set features.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingFeaturesPredicate} is {@code null}.
         */
        FromCopy removeAllFeatures(Predicate<Features> existingFeaturesPredicate);

        /**
         * Sets features to this builder which represents semantically {@code null}. All already set features are
         * discarded.
         *
         * @return this builder to allow method chaining.
         */
        FromCopy setNullFeatures();

        /**
         * Sets the given lifecycle to this builder.
         *
         * @param lifecycle the lifecycle to be set.
         * @return this builder to allow method chaining.
         */
        default FromCopy setLifecycle(@Nullable final ThingLifecycle lifecycle) {
            return setLifecycle(existingLifecycle -> true, lifecycle);
        }

        /**
         * Sets the given lifecycle to this builder.
         *
         * @param existingLifecyclePredicate a predicate to decide whether the given lifecycle is set. The predicate
         * receives the currently set lifecycle.
         * @param lifecycle the lifecycle to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingLifecyclePredicate} is {@code null}.
         */
        FromCopy setLifecycle(Predicate<ThingLifecycle> existingLifecyclePredicate, @Nullable ThingLifecycle lifecycle);

        /**
         * Sets the given revision to this builder.
         *
         * @param revision the revision to be set.
         * @return this builder to allow method chaining.
         */
        default FromCopy setRevision(final ThingRevision revision) {
            return setRevision(existingRevision -> true, revision);
        }

        /**
         * Sets the given revision to this builder.
         *
         * @param existingRevisionPredicate a predicate to decide whether the given revision is set. The predicate
         * receives the currently set revision.
         * @param revision the revision to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingRevisionPredicate} is {@code null}.
         */
        FromCopy setRevision(Predicate<ThingRevision> existingRevisionPredicate, ThingRevision revision);

        /**
         * Sets the given revision number to this builder.
         *
         * @param revisionNumber the revision number to be set.
         * @return this builder to allow method chaining.
         */
        default FromCopy setRevision(final long revisionNumber) {
            return setRevision(existingRevision -> true, revisionNumber);
        }

        /**
         * Sets the given revision number to this builder.
         *
         * @param existingRevisionPredicate a predicate to decide whether the given revision is set. The predicate
         * receives the currently set revision.
         * @param revisionNumber the revision number to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingRevisionPredicate} is {@code null}.
         */
        FromCopy setRevision(Predicate<ThingRevision> existingRevisionPredicate, long revisionNumber);

        /**
         * Sets the given modified to this builder.
         *
         * @param modified the modified to be set.
         * @return this builder to allow method chaining.
         */
        default FromCopy setModified(@Nullable final Instant modified) {
            return setModified(existingModified -> true, modified);
        }

        /**
         * Sets the given modified to this builder.
         *
         * @param existingModifiedPredicate a predicate to decide whether the given modified is set. The predicate
         * receives the currently set modified.
         * @param modified the modified to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingModifiedPredicate} is {@code null}.
         */
        FromCopy setModified(Predicate<Instant> existingModifiedPredicate, @Nullable Instant modified);

        /**
         * Sets the given Thing ID to this builder. The ID is required to include the Thing's namespace.
         *
         * @param thingId the Thing ID to be set.
         * @return this builder to allow method chaining.
         * @throws ThingIdInvalidException if {@code thingId} does not comply to
         * the required pattern.
         */
        default FromCopy setId(@Nullable final String thingId) {
            return setId(existingId -> true, thingId);
        }

        /**
         * Sets the given Thing ID to this builder. The ID is required to include the Thing's namespace.
         *
         * @param existingIdPredicate a predicate to decide whether the given ID is set. The predicate receives
         * the currently set Thing ID.
         * @param thingId the Thing ID to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingIdPredicate} is {@code null}.
         * @throws ThingIdInvalidException if {@code thingId} does not comply to
         * the required pattern.
         */
        FromCopy setId(Predicate<String> existingIdPredicate, @Nullable String thingId);

        /**
         * Sets a generated Thing ID to this builder.
         *
         * @return this builder to allow method chaining.
         */
        default FromCopy setGeneratedId() {
            return setGeneratedId(existingId -> true);
        }

        /**
         * Sets a generated Thing ID to this builder.
         *
         * @param existingIdPredicate a predicate to decide whether the given ID is set. The predicate receives
         * the currently set Thing ID.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code existingIdPredicate} is {@code null}.
         */
        FromCopy setGeneratedId(Predicate<String> existingIdPredicate);

        /**
         * Creates a new Thing object based on the data which was provided to this builder.
         *
         * @return a new Thing object.
         */
        Thing build();

    }

}
