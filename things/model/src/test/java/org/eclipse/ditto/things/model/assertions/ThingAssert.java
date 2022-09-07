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
package org.eclipse.ditto.things.model.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.base.model.assertions.AbstractJsonifiableAssert;
import org.eclipse.ditto.base.model.assertions.JsonifiableAssertions;
import org.eclipse.ditto.base.model.assertions.JsonifiableWithPredicateAssert;
import org.eclipse.ditto.base.model.assertions.JsonifiableWithSelectorAndPredicateAssert;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;

/**
 * Specific assertion for {@link org.eclipse.ditto.things.model.Thing} objects.
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

    public ThingAssert hasId(final ThingId expectedIdentifier) {
        isNotNull();

        final Optional<ThingId> actualIdOptional = actual.getEntityId();

        assertThat(actualIdOptional)
                .overridingErrorMessage("Expected Thing identifier to be \n<%s> but was \n<%s>",
                        expectedIdentifier.toString(),
                        String.valueOf(actualIdOptional.orElse(null)))
                .contains(expectedIdentifier);

        return this;
    }

    public ThingAssert hasNoId() {
        isNotNull();

        final Optional<ThingId> actualIdOptional = actual.getEntityId();

        assertThat(actualIdOptional)
                .overridingErrorMessage("Expected Thing not have an identifier but it had <%s>",
                        String.valueOf(actualIdOptional.orElse(null)))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasNamespace(final String expectedNamespace) {
        isNotNull();

        final Optional<String> actualNamespaceOptional = actual.getNamespace();

        assertThat(
                actualNamespaceOptional.isPresent() &&
                        Objects.equals(actualNamespaceOptional.get(), expectedNamespace))
                .overridingErrorMessage("Expected Thing namespace to be \n<%s> but was \n<%s>", expectedNamespace,
                        actualNamespaceOptional.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasNoNamespace() {
        isNotNull();

        final Optional<String> actualNamespaceOptional = actual.getNamespace();

        assertThat(actualNamespaceOptional)
                .overridingErrorMessage("Expected Thing not to have a namespace but it had <%s>",
                        actualNamespaceOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasPolicyId(final String expectedPolicyId) {
        isNotNull();

        final Optional<PolicyId> optionalPolicyId = actual.getPolicyId();

        assertThat(optionalPolicyId.isPresent() && Objects.equals(optionalPolicyId.get(), expectedPolicyId))
                .overridingErrorMessage("Expected Policy ID to be \n<%s> but was \n<%s>", expectedPolicyId,
                        optionalPolicyId.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasPolicyId(final PolicyId expectedPolicyId) {
        isNotNull();

        final Optional<PolicyId> optionalPolicyId = actual.getPolicyId();

        assertThat(optionalPolicyId.isPresent() && Objects.equals(optionalPolicyId.get(), expectedPolicyId))
                .overridingErrorMessage("Expected Policy ID to be \n<%s> but was \n<%s>", expectedPolicyId,
                        optionalPolicyId.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasNoPolicyId() {
        isNotNull();

        final Optional<PolicyId> policyIdOptional = actual.getPolicyId();

        assertThat(policyIdOptional)
                .overridingErrorMessage("Expected Thing not have a PolicyId but it had <%s>",
                        policyIdOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasAttributes(final Attributes expectedAttributes) {
        isNotNull();

        assertThat(actual.getAttributes())
                .overridingErrorMessage("Expected Thing Attributes to be <%s> but it did not", expectedAttributes)
                .contains(expectedAttributes);

        return this;
    }

    public ThingAssert hasNoAttributes() {
        isNotNull();

        final Optional<Attributes> attributesOptional = actual.getAttributes();
        assertThat(attributesOptional)
                .overridingErrorMessage("Expected Thing not to have any attributes but it had <%s>",
                        attributesOptional.orElse(null))
                .isEmpty();

        return this;
    }

    public ThingAssert hasAttribute(final JsonPointer attributePath, final JsonValue expectedValue) {
        isNotNull();

        final JsonValue actualAttributeValue = actual.getAttributes()
                .flatMap(attributes -> attributes.getValue(attributePath))
                .orElse(null);

        assertThat(actualAttributeValue)
                .overridingErrorMessage("Expected Thing attribute at <%s> to be \n<%s> but it was \n<%s>",
                        attributePath,
                        expectedValue, actualAttributeValue)
                .isEqualTo(expectedValue);

        return this;
    }

    public ThingAssert hasNotAttribute(final JsonPointer attributePath) {
        isNotNull();

        final boolean isAttributePresent = actual.getAttributes()
                .flatMap(attributes -> attributes.getValue(attributePath))
                .isPresent();

        assertThat(isAttributePresent)
                .overridingErrorMessage("Expected Thing not to have an attribute at <%s> but it did", attributePath)
                .isFalse();

        return this;
    }

    public ThingAssert hasDefinition(final ThingDefinition expectedDefinition) {
        isNotNull();

        assertThat(actual.getDefinition())
                .overridingErrorMessage("Expected Thing Definition to be <%s> but it did not", expectedDefinition)
                .contains(expectedDefinition);

        return this;
    }

    public ThingAssert hasDefinition(final String expectedDefinition) {
        isNotNull();

        final Optional<ThingDefinition> optionalDefinition = actual.getDefinition();

        assertThat(optionalDefinition.isPresent() &&
                optionalDefinition.get().toString().equals(expectedDefinition))
                .overridingErrorMessage("Expected Definition to be \n<%s> but was \n<%s>", expectedDefinition,
                        optionalDefinition.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasNoDefinition() {
        isNotNull();

        final Optional<ThingDefinition> definitionOptional = actual.getDefinition();

        assertThat(definitionOptional)
                .overridingErrorMessage("Expected Thing not have a Definition but it had <%s>",
                        definitionOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasFeature(final Feature expectedFeature) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures()
                .flatMap(features -> features.getFeature(expectedFeature.getId()));

        assertThat(featureOptional.isPresent() && Objects.equals(featureOptional.get(), expectedFeature))
                .overridingErrorMessage("Expected Thing to have Feature \n<%s> but it had \n<%s>", expectedFeature,
                        featureOptional.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasFeatureWithId(final String featureId) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId));

        assertThat(featureOptional.isPresent())
                .overridingErrorMessage("Expected Thing to have Feature with identifier <%s> but it had not",
                        featureId)
                .isTrue();

        return this;
    }

    public ThingAssert hasNotFeatureWithId(final String featureId) {
        isNotNull();

        final Optional<Feature> featureOptional = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId));

        assertThat(featureOptional)
                .overridingErrorMessage("Expected Thing not to have Feature with identifier <%s> but it had",
                        featureId)
                .isNotPresent();

        return this;
    }

    public ThingAssert hasFeatures(final Features expectedFeatures) {
        isNotNull();

        assertThat(actual.getFeatures())
                .overridingErrorMessage("Expected Thing Features to be <%s> but it did not", expectedFeatures)
                .contains(expectedFeatures);

        return this;
    }

    public ThingAssert hasNoFeatures() {
        isNotNull();

        final Optional<Features> featuresOptional = actual.getFeatures();
        assertThat(featuresOptional)
                .overridingErrorMessage("Expected Thing not to have any features but it had <%s>",
                        featuresOptional.orElse(null))
                .isEmpty();

        return this;
    }

    public ThingAssert hasFeatureProperties(final String featureId, final FeatureProperties expectedFeatureProperties) {
        isNotNull();

        final FeatureProperties actualProperties = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .orElse(null);

        assertThat(actualProperties)
                .overridingErrorMessage("Expected Thing Feature <%s> to have the properties \n<%s> but it had \n<%s>",
                        featureId, expectedFeatureProperties, actualProperties)
                .isEqualTo(expectedFeatureProperties);

        return this;
    }

    public ThingAssert featureHasNoProperties(final String featureId) {
        isNotNull();

        final boolean isFeatureHasProperties = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .isPresent();

        assertThat(isFeatureHasProperties)
                .overridingErrorMessage("Expected Thing Feature <%s> not to have any properties but it did",
                        featureId)
                .isFalse();

        return this;
    }

    public ThingAssert hasFeatureProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue expectedValue) {
        isNotNull();

        final JsonValue actualPropertyValue = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(feature -> feature.getProperty(propertyPath))
                .orElse(null);

        assertThat(actualPropertyValue)
                .overridingErrorMessage("Expected Thing Feature property at <%s> to be \n<%s> but it was \n<%s>",
                        propertyPath,
                        expectedValue, actualPropertyValue)
                .isEqualTo(expectedValue);

        return this;
    }

    public ThingAssert hasNotFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        isNotNull();

        final boolean isHasFeatureProperty = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(feature -> feature.getProperty(propertyPath))
                .isPresent();

        assertThat(isHasFeatureProperty)
                .overridingErrorMessage("Expected Thing Feature not to have a property at <%s> but it had.",
                        propertyPath)
                .isFalse();

        return this;
    }

    public ThingAssert hasFeatureDesiredProperties(final String featureId,
            final FeatureProperties expectedDesiredProperties) {
        isNotNull();

        final FeatureProperties actualDesiredProperties = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .orElse(null);

        assertThat(actualDesiredProperties)
                .overridingErrorMessage(
                        "Expected Thing Feature <%s> to have the desired properties \n<%s> but it had \n<%s>",
                        featureId, expectedDesiredProperties, actualDesiredProperties)
                .isEqualTo(expectedDesiredProperties);

        return this;
    }

    public ThingAssert featureHasNoDesiredProperties(final String featureId) {
        isNotNull();

        final boolean isFeatureHasDesiredProperties = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .isPresent();

        assertThat(isFeatureHasDesiredProperties)
                .overridingErrorMessage("Expected Thing Feature <%s> not to have any desired properties but it did",
                        featureId)
                .isFalse();

        return this;
    }

    public ThingAssert hasFeatureDesiredProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue expectedValue) {
        isNotNull();

        final JsonValue actualDesiredPropertyValue = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(feature -> feature.getDesiredProperty(propertyPath))
                .orElse(null);

        assertThat(actualDesiredPropertyValue)
                .overridingErrorMessage("Expected Thing Feature desired property at <%s> to be \n<%s> but " +
                                "it was \n<%s>",
                        propertyPath,
                        expectedValue, actualDesiredPropertyValue)
                .isEqualTo(expectedValue);

        return this;
    }

    public ThingAssert hasNotFeatureDesiredProperty(final String featureId, final JsonPointer propertyPath) {
        isNotNull();

        final boolean isHasDesiredFeatureProperty = actual.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(feature -> feature.getDesiredProperty(propertyPath))
                .isPresent();

        assertThat(isHasDesiredFeatureProperty)
                .overridingErrorMessage("Expected Thing Feature not to have a desired property at <%s> but it had.",
                        propertyPath)
                .isFalse();

        return this;
    }

    public ThingAssert hasLifecycle(final ThingLifecycle expectedLifecycle) {
        isNotNull();

        final Optional<ThingLifecycle> lifecycleOptional = actual.getLifecycle();

        assertThat(lifecycleOptional.isPresent() && Objects.equals(lifecycleOptional.get(), expectedLifecycle))
                .overridingErrorMessage("Expected Thing lifecycle to have lifecycle \n<%s> but it had \n<%s>",
                        expectedLifecycle, lifecycleOptional.orElse(null))
                .isTrue();

        return this;
    }

    public ThingAssert hasNoLifecycle() {
        isNotNull();

        final Optional<ThingLifecycle> actualLifecycleOptional = actual.getLifecycle();

        assertThat(actualLifecycleOptional)
                .overridingErrorMessage("Expected Thing not to have a lifecycle but it had <%s>",
                        actualLifecycleOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasRevision(final ThingRevision expectedRevision) {
        isNotNull();

        final Optional<ThingRevision> revisionOptional = actual.getRevision();

        assertThat(revisionOptional)
                .overridingErrorMessage("Expected Thing revision to be \n<%s> but it was \n<%s>", expectedRevision,
                        revisionOptional.orElse(null))
                .contains(expectedRevision);

        return this;
    }

    public ThingAssert hasNoRevision() {
        isNotNull();

        final Optional<ThingRevision> actualRevisionOptional = actual.getRevision();

        assertThat(actualRevisionOptional)
                .overridingErrorMessage("Expected Thing not have a revision but it had <%s>",
                        actualRevisionOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert hasModified(final Instant expectedmodified) {
        isNotNull();

        final Optional<Instant> modifiedOptional = actual.getModified();

        assertThat(modifiedOptional)
                .overridingErrorMessage("Expected Thing modified to be \n<%s> but it was \n<%s>", expectedmodified,
                        modifiedOptional.orElse(null))
                .contains(expectedmodified);

        return this;
    }

    /**
     *
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

        assertThat(actualmodifiedOptional)
                .overridingErrorMessage("Expected Thing not have a modified but it had <%s>",
                        actualmodifiedOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert isCreated() {
        isNotNull();
        final Optional<Instant> actualCreated = actual.getCreated();
        assertThat(actualCreated)
                .overridingErrorMessage("Expected Thing to be created but it was not")
                .isPresent();
        return this;
    }

    public ThingAssert hasCreated(final Instant expectedCreated) {
        isNotNull();

        final Optional<Instant> createdOptional = actual.getCreated();

        assertThat(createdOptional)
                .overridingErrorMessage("Expected Thing created to be \n<%s> but it was \n<%s>", expectedCreated,
                        createdOptional.orElse(null))
                .contains(expectedCreated);

        return this;
    }

    public ThingAssert hasNoCreated() {
        isNotNull();

        final Optional<Instant> actualCreatedOptional = actual.getCreated();

        assertThat(actualCreatedOptional)
                .overridingErrorMessage("Expected Thing not have a created but it had <%s>",
                        actualCreatedOptional.orElse(null))
                .isNotPresent();

        return this;
    }

    public ThingAssert isModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(modified.isAfter(Instant))
                .overridingErrorMessage("Expected <%s> to be after <%s> but it was not",
                        modified,
                        Instant)
                .isTrue();

        return this;
    }

    public ThingAssert isNotModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(!modified.isAfter(Instant))
                .overridingErrorMessage("Expected <%s> to be before <%s> but it was not",
                        modified,
                        Instant)
                .isTrue();

        return this;
    }

    public ThingAssert isEqualToButModified(final Thing expected) {
        assertThat(expected).isNotNull();
        assertThat(actual).isNotNull();

        assertThat(actual.getModified()).isPresent();
        assertThat(actual.getEntityId()).isEqualTo(expected.getEntityId());
        assertThat(actual.getAttributes()).isEqualTo(expected.getAttributes());
        assertThat(actual.getFeatures()).isEqualTo(expected.getFeatures());
        assertThat(actual.getPolicyId()).isEqualTo(expected.getPolicyId());

        return this;
    }

    public ThingAssert hasMetadata(final Metadata expectedMetadata) {
        isNotNull();

        final Optional<Metadata> metadataOptional = actual.getMetadata();

        assertThat(metadataOptional)
                .overridingErrorMessage("Expected Thing metadata to be \n<%s> but it was \n<%s>", expectedMetadata,
                        metadataOptional.orElse(null))
                .contains(expectedMetadata);

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
