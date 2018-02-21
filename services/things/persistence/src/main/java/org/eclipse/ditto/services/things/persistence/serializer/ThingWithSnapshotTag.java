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
package org.eclipse.ditto.services.things.persistence.serializer;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;

/**
 * This implementation of {@link Thing} delegates all calls to a wrapped Thing. Besides, this class offers a method
 * for retrieving a {@link SnapshotTag} and a snapshot sequence number which is persisted together with the wrapped
 * Thing.
 */
@Immutable
public final class ThingWithSnapshotTag implements Thing {

    private final Thing delegee;
    private final SnapshotTag snapshotTag;

    private ThingWithSnapshotTag(final Thing theDelegee, final SnapshotTag theSnapshotTag) {
        delegee = theDelegee;
        snapshotTag = theSnapshotTag;
    }

    /**
     * Returns a new instance of {@code ThingWithSnapshotTag} with a negative sequence number.
     *
     * @param delegee a Thing to be wrapped by the returned object.
     * @param snapshotTag the snapshot tag associated with {@code delegee}.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @Nonnull
    public static ThingWithSnapshotTag newInstance(final Thing delegee, final SnapshotTag snapshotTag) {
        checkNotNull(delegee, "delegee");
        checkNotNull(snapshotTag, "snapshot tag");

        if (delegee instanceof ThingWithSnapshotTag) {
            return newInstance(((ThingWithSnapshotTag) delegee).delegee, snapshotTag);
        } else {
            return new ThingWithSnapshotTag(delegee, snapshotTag);
        }
    }

    /**
     * Returns the snapshot tag of this Thing.
     *
     * @return the snapshot tag.
     */
    @Nonnull
    public SnapshotTag getSnapshotTag() {
        return snapshotTag;
    }

    @Override
    public Optional<ThingRevision> getRevision() {
        return delegee.getRevision();
    }

    @Override
    public Optional<Instant> getModified() {
        return delegee.getModified();
    }

    @Override
    public Optional<String> getId() {
        return delegee.getId();
    }

    @Override
    public Optional<String> getNamespace() {
        return delegee.getNamespace();
    }

    @Override
    public Optional<Attributes> getAttributes() {
        return delegee.getAttributes();
    }

    @Override
    public Thing setAttributes(@Nullable final Attributes attributes) {
        return replaceDelegee(delegee.setAttributes(attributes));
    }

    private Thing replaceDelegee(final Thing possiblyDifferentThing) {
        return newInstance(possiblyDifferentThing, snapshotTag);
    }

    @Override
    public Thing removeAttributes() {
        return replaceDelegee(delegee.removeAttributes());
    }

    @Override
    public Thing setAttribute(final JsonPointer attributePath, final JsonValue attributeValue) {
        return replaceDelegee(delegee.setAttribute(attributePath, attributeValue));
    }

    @Override
    public Thing removeAttribute(final JsonPointer attributePath) {
        return replaceDelegee(delegee.removeAttribute(attributePath));
    }

    @Override
    public Optional<Features> getFeatures() {
        return delegee.getFeatures();
    }

    @Override
    public Thing removeFeatures() {
        return replaceDelegee(delegee.removeFeatures());
    }

    @Override
    public Thing setFeatures(@Nullable final Features features) {
        return replaceDelegee(delegee.setFeatures(features));
    }

    @Override
    public Thing setFeature(final Feature feature) {
        return replaceDelegee(delegee.setFeature(feature));
    }

    @Override
    public Thing removeFeature(final String featureId) {
        return replaceDelegee(delegee.removeFeature(featureId));
    }

    @Override
    public Thing setFeatureDefinition(final String featureId, final FeatureDefinition definition) {
        return replaceDelegee(delegee.setFeatureDefinition(featureId, definition));
    }

    @Override
    public Thing removeFeatureDefinition(final String featureId) {
        return replaceDelegee(delegee.removeFeatureDefinition(featureId));
    }

    @Override
    public Thing setFeatureProperties(final String featureId, final FeatureProperties properties) {
        return replaceDelegee(delegee.setFeatureProperties(featureId, properties));
    }

    @Override
    public Thing removeFeatureProperties(final String featureId) {
        return replaceDelegee(delegee.removeFeatureProperties(featureId));
    }

    @Override
    public Thing setFeatureProperty(final String featureId, final JsonPointer propertyJsonPointer,
            final JsonValue propertyValue) {
        return replaceDelegee(delegee.setFeatureProperty(featureId, propertyJsonPointer, propertyValue));
    }

    @Override
    public Thing removeFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        return replaceDelegee(delegee.removeFeatureProperty(featureId, propertyPath));
    }

    @Override
    public Optional<AccessControlList> getAccessControlList() {
        return delegee.getAccessControlList();
    }

    @Override
    public Thing setAccessControlList(final AccessControlList accessControlList) {
        return replaceDelegee(delegee.setAccessControlList(accessControlList));
    }

    @Override
    public Thing setAclEntry(final AclEntry aclEntry) {
        return replaceDelegee(delegee.setAclEntry(aclEntry));
    }

    @Override
    public Thing removeAllPermissionsOf(final AuthorizationSubject authorizationSubject) {
        return replaceDelegee(delegee.removeAllPermissionsOf(authorizationSubject));
    }

    @Override
    public Optional<String> getPolicyId() {
        return delegee.getPolicyId();
    }

    @Override
    public Thing setPolicyId(@Nullable final String policyId) {
        return replaceDelegee(delegee.setPolicyId(policyId));
    }

    @Override
    public Optional<ThingLifecycle> getLifecycle() {
        return delegee.getLifecycle();
    }

    @Override
    public Thing setLifecycle(final ThingLifecycle newLifecycle) {
        return replaceDelegee(delegee.setLifecycle(newLifecycle));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return delegee.toJson(schemaVersion, thePredicate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThingWithSnapshotTag that = (ThingWithSnapshotTag) o;
        return Objects.equals(delegee, that.delegee) &&
                snapshotTag == that.snapshotTag;
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegee, snapshotTag);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "delegee=" + delegee + ", snapshotTag=" + snapshotTag + "]";
    }

}
