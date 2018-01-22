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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * A mutable builder with a fluent API for an immutable {@link Thing}. This builder is initialised with the properties
 * of an existing Thing.
 */
@NotThreadSafe
final class ImmutableThingFromCopyBuilder implements ThingBuilder, ThingBuilder.FromCopy {

    private final ImmutableThingFromScratchBuilder fromScratchBuilder;

    private ImmutableThingFromCopyBuilder() {
        fromScratchBuilder = ImmutableThingFromScratchBuilder.newInstance();
    }

    /**
     * Returns a new {@code ImmutableThingFromCopyBuilder} which is initialised with the properties of the given Thing.
     *
     * @param thing an existing Thing which provides the properties of the new Thing.
     * @return the new builder.
     * @throws NullPointerException if {@code thing} is {@code null}.
     */
    public static ImmutableThingFromCopyBuilder of(final Thing thing) {
        checkNotNull(thing, "Thing");

        final ImmutableThingFromCopyBuilder result = new ImmutableThingFromCopyBuilder();
        thing.getId().ifPresent(result::setId);
        thing.getAccessControlList().ifPresent(result::setPermissions);
        thing.getPolicyId().ifPresent(result::setPolicyId);
        thing.getAttributes().ifPresent(result::setAttributes);
        thing.getFeatures().ifPresent(result::setFeatures);
        thing.getLifecycle().ifPresent(result::setLifecycle);
        thing.getRevision().ifPresent(result::setRevision);
        thing.getModified().ifPresent(result::setModified);

        return result;
    }

    /**
     * Returns a new {@code ImmutableThingFromCopyBuilder} which is initialised with the properties of parsed Thing JSON
     * object. The JSON object is parsed in a very fault tolerant way. I. e. all properties which cannot be deserialized
     * are supposed to not exist.
     *
     * @param jsonObject JSON object representation of an existing Thing.
     * @return the new builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonParseException if {@code jsonObject} was not in the expected "thing-schema" format.
     */
    public static ImmutableThingFromCopyBuilder of(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        final ImmutableThingFromCopyBuilder result = new ImmutableThingFromCopyBuilder();

        jsonObject.getValue(Thing.JsonFields.ID).ifPresent(result::setId);

        jsonObject.getValue(Thing.JsonFields.ACL)
                .map(ThingsModelFactory::newAcl)
                .ifPresent(result::setPermissions);

        jsonObject.getValue(Thing.JsonFields.POLICY_ID).ifPresent(result::setPolicyId);

        jsonObject.getValue(Thing.JsonFields.ATTRIBUTES)
                .map(ThingsModelFactory::newAttributes)
                .ifPresent(result::setAttributes);

        jsonObject.getValue(Thing.JsonFields.FEATURES)
                .map(ThingsModelFactory::newFeatures)
                .ifPresent(result::setFeatures);

        jsonObject.getValue(Thing.JsonFields.LIFECYCLE)
                .flatMap(ThingLifecycle::forName)
                .ifPresent(result::setLifecycle);

        jsonObject.getValue(Thing.JsonFields.REVISION)
                .map(ThingsModelFactory::newThingRevision)
                .ifPresent(result::setRevision);

        jsonObject.getValue(Thing.JsonFields.MODIFIED)
                .map(ImmutableThingFromCopyBuilder::tryToParseModified)
                .ifPresent(result::setModified);

        return result;
    }

    private static Instant tryToParseModified(final CharSequence dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            final String msgPattern ="The JSON object's field <{0>' is not in ISO-8601 format as expected!";
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format(msgPattern, Thing.JsonFields.MODIFIED.getPointer()))
                    .cause(e)
                    .build();
        }
    }

    @Override
    public FromCopy setPermissions(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {

        fromScratchBuilder.setPermissions(authorizationSubject, permission, furtherPermissions);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate,
            final AuthorizationSubject authorizationSubject,
            final Permission permission,
            final Permission... furtherPermissions) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(authorizationSubject, permission, furtherPermissions);
        }
        return this;
    }

    @Override
    public FromCopy setPermissions(final AuthorizationSubject authorizationSubject, final Permissions permissions) {
        fromScratchBuilder.setPermissions(authorizationSubject, permissions);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate,
            final AuthorizationSubject authorizationSubject, final Permissions permissions) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(authorizationSubject, permissions);
        }
        return this;
    }

    @Override
    public FromCopy setPermissions(final Iterable<AclEntry> aclEntries) {
        fromScratchBuilder.setPermissions(aclEntries);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate,
            final Iterable<AclEntry> aclEntries) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(aclEntries);
        }
        return this;
    }

    @Override
    public FromCopy setPermissions(final JsonObject accessControlListJsonObject) {
        fromScratchBuilder.setPermissions(accessControlListJsonObject);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate,
            final JsonObject accessControlListJsonObject) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(accessControlListJsonObject);
        }
        return this;
    }

    @Override
    public FromCopy setPermissions(final String accessControlListJsonString) {
        fromScratchBuilder.setPermissions(accessControlListJsonString);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate,
            final String accessControlListJsonString) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(accessControlListJsonString);
        }
        return this;
    }

    @Override
    public FromCopy setPermissions(final AclEntry aclEntry, final AclEntry... furtherAclEntries) {
        fromScratchBuilder.setPermissions(aclEntry, furtherAclEntries);
        return this;
    }

    @Override
    public FromCopy setPermissions(final Predicate<AccessControlList> existingAclPredicate, final AclEntry aclEntry,
            final AclEntry... furtherAclEntries) {

        if (testAclPredicate(existingAclPredicate)) {
            setPermissions(aclEntry, furtherAclEntries);
        }
        return this;
    }

    @Override
    public FromCopy removePermissionsOf(final AuthorizationSubject authorizationSubject) {
        fromScratchBuilder.removePermissionsOf(authorizationSubject);
        return this;
    }

    @Override
    public FromCopy removePermissionsOf(final Predicate<AccessControlList> existingAclPredicate,
            final AuthorizationSubject authorizationSubject) {

        if (testAclPredicate(existingAclPredicate)) {
            removePermissionsOf(authorizationSubject);
        }
        return this;
    }

    @Override
    public FromCopy removeAllPermissions() {
        fromScratchBuilder.removeAllPermissions();
        return this;
    }

    @Override
    public FromCopy removeAllPermissions(final Predicate<AccessControlList> existingAclPredicate) {
        if (testAclPredicate(existingAclPredicate)) {
            return removeAllPermissions();
        }
        return this;
    }

    @Override
    public FromCopy setPolicyId(@Nullable final String policyId) {
        fromScratchBuilder.setPolicyId(policyId);
        return this;
    }

    @Override
    public FromCopy removePolicyId() {
        fromScratchBuilder.removePolicyId();
        return this;
    }

    @Override
    public FromCopy setAttributes(final Attributes attributes) {
        fromScratchBuilder.setAttributes(attributes);
        return this;
    }

    @Override
    public FromCopy setAttributes(final Predicate<Attributes> existingAttributesPredicate,
            final Attributes attributes) {

        if (testAttributesPredicate(existingAttributesPredicate)) {
            return setAttributes(attributes);
        }
        return this;
    }

    @Override
    public FromCopy setAttributes(final JsonObject attributesJsonObject) {
        fromScratchBuilder.setAttributes(attributesJsonObject);
        return this;
    }

    @Override
    public FromCopy setAttributes(final Predicate<Attributes> existingAttributesPredicate,
            final JsonObject attributesJsonObject) {

        if (testAttributesPredicate(existingAttributesPredicate)) {
            setAttributes(attributesJsonObject);
        }
        return this;
    }

    @Override
    public FromCopy setAttributes(final String attributesJsonString) {
        return setAttributes(ThingsModelFactory.newAttributes(attributesJsonString));
    }

    @Override
    public FromCopy setAttributes(final Predicate<Attributes> existingAttributesPredicate,
            final String attributesJsonString) {

        if (testAttributesPredicate(existingAttributesPredicate)) {
            return setAttributes(attributesJsonString);
        }
        return this;
    }

    @Override
    public FromCopy removeAllAttributes() {
        fromScratchBuilder.removeAllAttributes();
        return this;
    }

    @Override
    public FromCopy removeAttribute(final JsonPointer attributePath) {
        fromScratchBuilder.removeAttribute(attributePath);
        return this;
    }

    @Override
    public FromCopy removeAttribute(final Predicate<Attributes> existingAttributesPredicate,
            final JsonPointer attributePath) {

        if (testAttributesPredicate(existingAttributesPredicate)) {
            removeAttribute(attributePath);
        }
        return this;
    }

    @Override
    public FromCopy removeAllAttributes(final Predicate<Attributes> existingAttributesPredicate) {
        if (testAttributesPredicate(existingAttributesPredicate)) {
            removeAllAttributes();
        }
        return this;
    }

    @Override
    public FromCopy setNullAttributes() {
        fromScratchBuilder.setNullAttributes();
        return this;
    }

    @Override
    public FromCopy setAttribute(final JsonPointer attributePath, final JsonValue attributeValue) {
        fromScratchBuilder.setAttribute(attributePath, attributeValue);
        return this;
    }

    @Override
    public FromCopy setAttribute(final Predicate<Attributes> existingAttributesPredicate,
            final JsonPointer attributePath, final JsonValue attributeValue) {

        if (testAttributesPredicate(existingAttributesPredicate)) {
            setAttribute(attributePath, attributeValue);
        }
        return this;
    }

    @Override
    public FromCopy setFeature(final Feature feature) {
        fromScratchBuilder.setFeature(feature);
        return this;
    }

    @Override
    public FromCopy setFeature(final Predicate<Features> existingFeaturesPredicate, final Feature feature) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeature(feature);
        }
        return this;
    }

    @Override
    public FromCopy setFeature(final String featureId) {
        return setFeature(ThingsModelFactory.newFeature(featureId));
    }

    @Override
    public FromCopy setFeature(final Predicate<Features> existingFeaturesPredicate, final String featureId) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeature(featureId);
        }
        return this;
    }

    @Override
    public FromCopy removeFeature(final String featureId) {
        final Features features = fromScratchBuilder.getFeatures();
        if (null != features && !features.isEmpty() && !features.isNull()) {
            fromScratchBuilder.removeFeature(featureId);
            if (fromScratchBuilder.getFeatures() == null) {
                fromScratchBuilder.setEmptyFeatures();
            }
        }
        return this;
    }

    @Override
    public FromCopy removeFeature(final Predicate<Features> existingFeaturesPredicate, final String featureId) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            removeFeature(featureId);
        }
        return this;
    }

    @Override
    public FromCopy setFeature(final Predicate<Features> existingFeaturesPredicate,
            final String featureId,
            final FeatureDefinition featureDefinition,
            final FeatureProperties featureProperties) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeature(featureId, featureDefinition, featureProperties);
        }
        return this;
    }

    @Override
    public FromCopy setFeature(final String featureId, final FeatureDefinition featureDefinition,
            final FeatureProperties featureProperties) {

        fromScratchBuilder.setFeature(featureId, featureDefinition, featureProperties);
        return this;
    }

    @Override
    public FromCopy setFeature(final String featureId, final FeatureProperties featureProperties) {
        fromScratchBuilder.setFeature(featureId, featureProperties);
        return this;
    }

    @Override
    public FromCopy setFeature(final Predicate<Features> existingFeaturesPredicate, final String featureId,
            final FeatureProperties featureProperties) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeature(featureId, featureProperties);
        }
        return this;
    }

    @Override
    public FromCopy setFeatureDefinition(final Predicate<Features> existingFeaturesPredicate, final String featureId,
            final FeatureDefinition featureDefinition) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            fromScratchBuilder.setFeatureDefinition(featureId, featureDefinition);
        }
        return this;
    }

    @Override
    public FromCopy removeFeatureDefinition(final String featureId) {
        fromScratchBuilder.removeFeatureDefinition(featureId);
        return this;
    }

    @Override
    public FromCopy setFeatureProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue propertyValue) {

        fromScratchBuilder.setFeatureProperty(featureId, propertyPath, propertyValue);
        return this;
    }

    @Override
    public FromCopy setFeatureProperty(final Predicate<Features> existingFeaturesPredicate,
            final String featureId,
            final JsonPointer propertyPath,
            final JsonValue propertyValue) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            fromScratchBuilder.setFeatureProperty(featureId, propertyPath, propertyValue);
        }
        return this;
    }

    @Override
    public FromCopy removeFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        fromScratchBuilder.removeFeatureProperty(featureId, propertyPath);
        return this;
    }

    @Override
    public FromCopy removeFeatureProperty(final Predicate<Features> existingFeaturesPredicate, final String featureId,
            final JsonPointer propertyPath) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            fromScratchBuilder.removeFeatureProperty(featureId, propertyPath);
        }
        return this;
    }

    @Override
    public FromCopy setFeatureProperties(final Predicate<Features> existingFeaturesPredicate, final String featureId,
            final FeatureProperties featureProperties) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            fromScratchBuilder.setFeatureProperties(featureId, featureProperties);
        }
        return this;
    }

    @Override
    public FromCopy removeFeatureProperties(final Predicate<Features> existingFeaturesPredicate,
            final String featureId) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            fromScratchBuilder.removeFeatureProperties(featureId);
        }
        return this;
    }

    @Override
    public FromCopy setFeatures(final JsonObject featuresJsonObject) {
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonObject));
    }

    @Override
    public FromCopy setFeatures(final Predicate<Features> existingFeaturesPredicate,
            final JsonObject featuresJsonObject) {

        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeatures(featuresJsonObject);
        }
        return this;
    }

    @Override
    public FromCopy setFeatures(final String featuresJsonString) {
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonString));
    }

    @Override
    public FromCopy setFeatures(final Predicate<Features> existingFeaturesPredicate, final String featuresJsonString) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeatures(featuresJsonString);
        }
        return this;
    }

    @Override
    public FromCopy setFeatures(final Iterable<Feature> features) {
        fromScratchBuilder.setFeatures(features);
        return this;
    }

    @Override
    public FromCopy setFeatures(final Predicate<Features> existingFeaturesPredicate, final Iterable<Feature> features) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            setFeatures(features);
        }
        return this;
    }

    @Override
    public FromCopy removeAllFeatures() {
        fromScratchBuilder.removeAllFeatures();
        return this;
    }

    @Override
    public FromCopy removeAllFeatures(final Predicate<Features> existingFeaturesPredicate) {
        if (testFeaturesPredicate(existingFeaturesPredicate)) {
            removeAllFeatures();
        }
        return this;
    }

    @Override
    public FromCopy setNullFeatures() {
        fromScratchBuilder.setNullFeatures();
        return this;
    }

    @Override
    public FromCopy setLifecycle(@Nullable final ThingLifecycle lifecycle) {
        fromScratchBuilder.setLifecycle(lifecycle);
        return this;
    }

    @Override
    public FromCopy setLifecycle(final Predicate<ThingLifecycle> existingLifecyclePredicate,
            @Nullable final ThingLifecycle lifecycle) {

        if (existingLifecyclePredicate.test(fromScratchBuilder.lifecycle)) {
            setLifecycle(lifecycle);
        }
        return this;
    }

    @Override
    public FromCopy setRevision(@Nullable final ThingRevision revision) {
        fromScratchBuilder.setRevision(revision);
        return this;
    }

    @Override
    public FromCopy setRevision(final Predicate<ThingRevision> existingRevisionPredicate,
            @Nullable final ThingRevision revision) {

        if (existingRevisionPredicate.test(fromScratchBuilder.revision)) {
            setRevision(revision);
        }
        return this;
    }

    @Override
    public FromCopy setRevision(final long revisionNumber) {
        fromScratchBuilder.setRevision(revisionNumber);
        return this;
    }

    @Override
    public FromCopy setRevision(final Predicate<ThingRevision> existingRevisionPredicate, final long revisionNumber) {
        if (existingRevisionPredicate.test(fromScratchBuilder.revision)) {
            setRevision(revisionNumber);
        }
        return this;
    }

    @Override
    public FromCopy setModified(@Nullable final Instant modified) {
        fromScratchBuilder.setModified(modified);
        return this;
    }

    @Override
    public FromCopy setModified(final Predicate<Instant> existingModifiedPredicate, @Nullable final Instant modified) {
        if (existingModifiedPredicate.test(fromScratchBuilder.modified)) {
            setModified(modified);
        }
        return this;
    }

    @Override
    public FromCopy setId(@Nullable final String thingId) {
        fromScratchBuilder.setId(thingId);
        return this;
    }

    @Override
    public FromCopy setId(final Predicate<String> existingIdPredicate, @Nullable final String thingId) {
        if (existingIdPredicate.test(fromScratchBuilder.id)) {
            setId(thingId);
        }
        return this;
    }

    @Override
    public FromCopy setGeneratedId() {
        fromScratchBuilder.setGeneratedId();
        return this;
    }

    @Override
    public FromCopy setGeneratedId(final Predicate<String> existingIdPredicate) {
        if (existingIdPredicate.test(fromScratchBuilder.id)) {
            setGeneratedId();
        }
        return this;
    }

    @Override
    public Thing build() {
        return fromScratchBuilder.build();
    }

    private boolean testAclPredicate(final Predicate<AccessControlList> existingAclPredicate) {
        return existingAclPredicate.test(fromScratchBuilder.getAcl());
    }

    private boolean testAttributesPredicate(final Predicate<Attributes> existingAttributesPredicate) {
        return existingAttributesPredicate.test(fromScratchBuilder.getAttributes());
    }

    private boolean testFeaturesPredicate(final Predicate<Features> existingFeaturesPredicate) {
        checkNotNull(existingFeaturesPredicate, "predicate for existing Features");
        return existingFeaturesPredicate.test(fromScratchBuilder.getFeatures());
    }

}
