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
package org.eclipse.ditto.model.things.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.model.base.assertions.JsonifiableAssertions;
import org.eclipse.ditto.model.base.assertions.JsonifiableWithPredicateAssert;
import org.eclipse.ditto.model.base.assertions.JsonifiableWithSelectorAndPredicateAssert;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Permissions;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;

/**
 * Specific assertion for {@link Thing} objects.
 */
public final class ThingAssert extends AbstractJsonifiableAssert<ThingAssert, Thing> implements
        JsonifiableWithPredicateAssert<JsonObject, JsonField, Thing, ThingAssert>,
        JsonifiableWithSelectorAndPredicateAssert<JsonField, Thing, ThingAssert> {

    /**
     * Constructs a new {@code ThingAssert} object.
     *
     * @param actual the actual Thing.
     */
    ThingAssert(final Thing actual) {
        super(actual, ThingAssert.class);
    }

    public ThingAssert hasId(final String expectedIdentifier) {
        isNotNull();

        final Optional<String> actualIdOptional = actual.getId();

        assertThat(actualIdOptional.isPresent() && Objects.equals(actualIdOptional.get(), expectedIdentifier)) //
                .overridingErrorMessage("Expected Thing identifier to be \n<%s> but was \n<%s>", expectedIdentifier,
                        actualIdOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNoId() {
        isNotNull();

        final Optional<String> actualIdOptional = actual.getId();

        assertThat(actualIdOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not have an identifier but it had <%s>",
                        actualIdOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasNamespace(final String expectedNamespace) {
        isNotNull();

        final Optional<String> actualNamespaceOptional = actual.getNamespace();

        assertThat(
                actualNamespaceOptional.isPresent() &&
                        Objects.equals(actualNamespaceOptional.get(), expectedNamespace)) //
                .overridingErrorMessage("Expected Thing namespace to be \n<%s> but was \n<%s>", expectedNamespace,
                        actualNamespaceOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNoNamespace() {
        isNotNull();

        final Optional<String> actualNamespaceOptional = actual.getNamespace();

        assertThat(actualNamespaceOptional.isPresent())
                .overridingErrorMessage("Expected Thing not to have a namespace but it had <%s>",
                        actualNamespaceOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasPolicyId(final String expectedPolicyId) {
        isNotNull();

        final Optional<String> optionalPolicyId = actual.getPolicyId();

        assertThat(optionalPolicyId.isPresent() && Objects.equals(optionalPolicyId.get(), expectedPolicyId)) //
                .overridingErrorMessage("Expected Policy ID to be \n<%s> but was \n<%s>", expectedPolicyId,
                        optionalPolicyId.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNoPolicyId() {
        isNotNull();

        final Optional<String> policyIdOptional = actual.getPolicyId();

        assertThat(policyIdOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not have a PolicyId but it had <%s>",
                        policyIdOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasAcl(final AccessControlList expectedAcl) {
        isNotNull();

        final Optional<AccessControlList> aclOptional = actual.getAccessControlList();

        assertThat(aclOptional.isPresent() && Objects.equals(aclOptional.get(), expectedAcl)) //
                .overridingErrorMessage("Expected Thing ACL to be \n<%s> but was \n<%s>", expectedAcl,
                        aclOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNoAcl() {
        isNotNull();

        final Optional<AccessControlList> accessControlListOptional = actual.getAccessControlList();

        assertThat(accessControlListOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not have an Access Control List but it had <%s>",
                        accessControlListOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasAclEntry(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        return hasAclEntry(authorizationSubject, ThingsModelFactory.newPermissions(permission, furtherPermissions));
    }

    public ThingAssert hasAclEntry(final AuthorizationSubject authorizationSubject,
            final Collection<Permission> expectedPermissions) {
        isNotNull();

        final Permissions actualPermissions = actual.getAccessControlList() //
                .map(acl -> acl.getPermissionsOf(authorizationSubject)) //
                .orElse(ThingsModelFactory.noPermissions());

        assertThat(actualPermissions.containsAll(expectedPermissions)) //
                .overridingErrorMessage("Expected Thing ACL to contain an entry for <%s> with the permission(s) \n<%s> "
                        + "but it contained \n<%s>", authorizationSubject, expectedPermissions, actualPermissions) //
                .isTrue();

        return this;
    }

    public ThingAssert hasAclEntry(final AclEntry expectedAclEntry) {
        isNotNull();

        boolean isHasAclEntry = false;
        final Optional<AccessControlList> accessControlListOptional = actual.getAccessControlList();
        if (accessControlListOptional.isPresent()) {
            isHasAclEntry = accessControlListOptional.get() //
                    .stream() //
                    .filter(actualAclEntry -> Objects.equals(actualAclEntry, expectedAclEntry)) //
                    .findAny() //
                    .isPresent();
        }

        assertThat(isHasAclEntry) //
                .overridingErrorMessage("Expected Thing ACL to contain the entry <%s> but it did not",
                        expectedAclEntry) //
                .isTrue();

        return this;
    }

    public ThingAssert hasAttributes(final Attributes expectedAttributes) {
        isNotNull();

        assertThat(actual.getAttributes()) //
                .overridingErrorMessage("Expected Thing Attributes to be <%s> but it did not", expectedAttributes) //
                .contains(expectedAttributes);

        return this;
    }

    public ThingAssert hasNoAttributes() {
        isNotNull();

        final Optional<Attributes> attributesOptional = actual.getAttributes();
        assertThat(attributesOptional) //
                .overridingErrorMessage("Expected Thing not to have any attributes but it had <%s>",
                        attributesOptional.orElse(null)) //
                .isEmpty();

        return this;
    }

    public ThingAssert hasAttribute(final JsonPointer attributePath, final JsonValue expectedValue) {
        isNotNull();

        final JsonValue actualAttributeValue = actual.getAttributes() //
                .flatMap(attributes -> attributes.getValue(attributePath)) //
                .orElse(null);

        assertThat(actualAttributeValue) //
                .overridingErrorMessage("Expected Thing attribute at <%s> to be \n<%s> but it was \n<%s>",
                        attributePath,
                        expectedValue, actualAttributeValue) //
                .isEqualTo(expectedValue);

        return this;
    }

    public ThingAssert hasNotAttribute(final JsonPointer attributePath) {
        isNotNull();

        final boolean isAttributePresent = actual.getAttributes() //
                .flatMap(attributes -> attributes.getValue(attributePath)) //
                .isPresent();

        assertThat(isAttributePresent)
                .overridingErrorMessage("Expected Thing not to have an attribute at <%s> but it did", attributePath) //
                .isFalse();

        return this;
    }

    public ThingAssert hasFeature(final Feature expectedFeature) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures() //
                .flatMap(features -> features.getFeature(expectedFeature.getId()));

        assertThat(featureOptional.isPresent() && Objects.equals(featureOptional.get(), expectedFeature))
                .overridingErrorMessage("Expected Thing to have Feature \n<%s> but it had \n<%s>", expectedFeature,
                        featureOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasFeatureWithId(final String featureId) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId));

        assertThat(featureOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing to have Feature with identifier <%s> but it had not",
                        featureId) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNotFeatureWithId(final String featureId) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId));

        assertThat(!featureOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not to have Feature with identifier <%s> but it had",
                        featureId) //
                .isTrue();

        return this;
    }

    public ThingAssert hasFeatures(final Features expectedFeatures) {
        isNotNull();

        assertThat(actual.getFeatures()) //
                .overridingErrorMessage("Expected Thing Features to be <%s> but it did not", expectedFeatures) //
                .contains(expectedFeatures);

        return this;
    }

    public ThingAssert hasNoFeatures() {
        isNotNull();

        final Optional<Features> featuresOptional = actual.getFeatures();
        assertThat(featuresOptional) //
                .overridingErrorMessage("Expected Thing not to have any features but it had <%s>",
                        featuresOptional.orElse(null)) //
                .isEmpty();

        return this;
    }

    public ThingAssert hasFeatureProperties(final String featureId, final FeatureProperties expectedFeatureProperties) {
        isNotNull();

        final FeatureProperties actualProperties = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId)) //
                .flatMap(Feature::getProperties) //
                .orElse(null);

        assertThat(actualProperties) //
                .overridingErrorMessage("Expected Thing Feature <%s> to have the properties \n<%s> but it had \n<%s>",
                        featureId, expectedFeatureProperties, actualProperties) //
                .isEqualTo(expectedFeatureProperties);

        return this;
    }

    public ThingAssert featureHasNoProperties(final String featureId) {
        isNotNull();

        final boolean isFeatureHasProperties = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId)) //
                .flatMap(Feature::getProperties) //
                .isPresent();

        assertThat(isFeatureHasProperties) //
                .overridingErrorMessage("Expected Thing Feature <%s> not to have any properties but it did",
                        featureId) //
                .isFalse();

        return this;
    }

    public ThingAssert hasFeatureProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue expectedValue) {
        isNotNull();

        final JsonValue actualPropertyValue = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId)) //
                .flatMap(feature -> feature.getProperty(propertyPath)) //
                .orElse(null);

        assertThat(actualPropertyValue) //
                .overridingErrorMessage("Expected Thing Feature property at <%s> to be \n<%s> but it was \n<%s>",
                        propertyPath,
                        expectedValue, actualPropertyValue) //
                .isEqualTo(expectedValue);

        return this;
    }

    public ThingAssert hasNotFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        isNotNull();

        final boolean isHasFeatureProperty = actual.getFeatures() //
                .flatMap(features -> features.getFeature(featureId)) //
                .flatMap(feature -> feature.getProperty(propertyPath)) //
                .isPresent();

        assertThat(isHasFeatureProperty) //
                .overridingErrorMessage("Expected Thing Feature not to have a property at <%s> but it had.",
                        propertyPath) //
                .isFalse();

        return this;
    }

    public ThingAssert hasLifecycle(final ThingLifecycle expectedLifecycle) {
        isNotNull();

        final Optional<ThingLifecycle> lifecycleOptional = actual.getLifecycle();

        assertThat(lifecycleOptional.isPresent() && Objects.equals(lifecycleOptional.get(), expectedLifecycle)) //
                .overridingErrorMessage("Expected Thing lifecycle to have lifecycle \n<%s> but it had \n<%s>",
                        expectedLifecycle, lifecycleOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public ThingAssert hasNoLifecycle() {
        isNotNull();

        final Optional<ThingLifecycle> actualLifecycleOptional = actual.getLifecycle();

        assertThat(actualLifecycleOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not to have a lifecycle but it had <%s>",
                        actualLifecycleOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasRevision(final ThingRevision expectedRevision) {
        isNotNull();

        final Optional<ThingRevision> revisionOptional = actual.getRevision();

        assertThat(revisionOptional) //
                .overridingErrorMessage("Expected Thing revision to be \n<%s> but it was \n<%s>", expectedRevision,
                        revisionOptional.orElse(null)) //
                .contains(expectedRevision);

        return this;
    }

    public ThingAssert hasNoRevision() {
        isNotNull();

        final Optional<ThingRevision> actualRevisionOptional = actual.getRevision();

        assertThat(actualRevisionOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not have a revision but it had <%s>",
                        actualRevisionOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert hasModified(final Instant expectedmodified) {
        isNotNull();

        final Optional<Instant> modifiedOptional = actual.getModified();

        assertThat(modifiedOptional) //
                .overridingErrorMessage("Expected Thing modified to be \n<%s> but it was \n<%s>", expectedmodified,
                        modifiedOptional.orElse(null)) //
                .contains(expectedmodified);

        return this;
    }

    /**
     */
    public ThingAssert isModified() {
        isNotNull();
        final Optional<Instant> actualModified = actual.getModified();
        assertThat(actualModified)
                .overridingErrorMessage("Expected Thing to be modified but it was not")
                .isPresent();
        return this;
    }

    public ThingAssert hasNoModified() {
        isNotNull();

        final Optional<Instant> actualmodifiedOptional = actual.getModified();

        assertThat(actualmodifiedOptional.isPresent()) //
                .overridingErrorMessage("Expected Thing not have a modified but it had <%s>",
                        actualmodifiedOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public ThingAssert isModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(modified.isAfter(Instant)) //
                .overridingErrorMessage("Expected <%s> to be after <%s> but it was not",
                        modified,
                        Instant) //
                .isTrue();

        return this;
    }

    public ThingAssert isNotModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(!modified.isAfter(Instant)) //
                .overridingErrorMessage("Expected <%s> to be before <%s> but it was not",
                        modified,
                        Instant) //
                .isTrue();

        return this;
    }

    public ThingAssert isEqualToButModified(final Thing expected) {
        assertThat(expected).isNotNull();
        assertThat(actual).isNotNull();

        assertThat(actual.getModified()).isPresent();
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getAttributes()).isEqualTo(expected.getAttributes());
        assertThat(actual.getFeatures()).isEqualTo(expected.getFeatures());

        if (JsonSchemaVersion.V_1.equals(expected.getImplementedSchemaVersion())) {
            assertThat(actual.getAccessControlList()).isEqualTo(expected.getAccessControlList());
        }
        if (JsonSchemaVersion.V_2.equals(expected.getImplementedSchemaVersion())) {
            assertThat(actual.getPolicyId()).isEqualTo(expected.getPolicyId());
        }

        return this;
    }

    @Override
    public ThingAssert hasEqualJson(final Thing expected, final JsonFieldSelector fieldSelector,
            final Predicate<JsonField> predicate) {
        JsonifiableAssertions.hasEqualJson(actual, expected, fieldSelector, predicate);

        return this;
    }

    @Override
    public ThingAssert hasEqualJson(final Thing expected, final Predicate<JsonField> predicate) {
        JsonifiableAssertions.hasEqualJson(actual, expected, predicate);

        return this;
    }
}
