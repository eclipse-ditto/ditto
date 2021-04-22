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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * A mutable builder for an immutable {@link Thing} from scratch.
 */
@NotThreadSafe
final class ImmutableThingFromScratchBuilder implements ThingBuilder, ThingBuilder.FromScratch {

    /*
     * This builder is used by ImmutableThingFromCopyBuilder to reduce implementation overhead.
     * Thus some fields and methods have to be package private - unfortunately.
     */

    @Nullable ThingId id;
    @Nullable ThingLifecycle lifecycle;
    @Nullable ThingRevision revision;
    @Nullable Instant modified;
    @Nullable Instant created;
    @Nullable Metadata metadata;
    @Nullable private PolicyId policyId;
    @Nullable private AttributesBuilder attributesBuilder;
    @Nullable private Attributes attributes;
    @Nullable private ThingDefinition definition;
    @Nullable private FeaturesBuilder featuresBuilder;
    @Nullable private Features features;

    private ImmutableThingFromScratchBuilder() {
        id = null;
        policyId = null;
        attributesBuilder = null;
        attributes = null;
        definition = null;
        featuresBuilder = null;
        features = null;
        lifecycle = null;
        revision = null;
        modified = null;
        created = null;
        metadata = null;
    }

    /**
     * Returns a new instance of {@code ImmutableThingFromScratchBuilder}.
     *
     * @return the new builder.
     */
    public static ImmutableThingFromScratchBuilder newInstance() {
        return new ImmutableThingFromScratchBuilder();
    }

    @Override
    public FromScratch setAttributes(final Attributes attributes) {
        checkNotNull(attributes, "Attributes to be set");

        if (attributes.isNull()) {
            return setNullAttributes();
        } else {
            invokeOnAttributesBuilder(ab -> ab.removeAll().setAll(attributes));
            return this;
        }
    }

    @Override
    public FromScratch setAttributes(final JsonObject attributesJsonObject) {
        checkNotNull(attributesJsonObject, "JSON object representation of Attributes to be set");

        if (attributesJsonObject.isNull()) {
            return setNullAttributes();
        } else if (attributesJsonObject.isEmpty()) {
            return setEmptyAttributes();
        }
        return setAttributes(ThingsModelFactory.newAttributes(attributesJsonObject));
    }

    @Override
    public FromScratch setAttributes(final String attributesJsonString) {
        return setAttributes(ThingsModelFactory.newAttributes(attributesJsonString));
    }

    @Override
    public FromScratch removeAllAttributes() {
        attributesBuilder = null;
        return this;
    }

    @Override
    public FromScratch setEmptyAttributes() {
        attributesBuilder = null;
        attributes = ThingsModelFactory.emptyAttributes();
        return this;
    }

    @Override
    public FromScratch setNullAttributes() {
        attributesBuilder = null;
        attributes = ThingsModelFactory.nullAttributes();
        return this;
    }

    @Override
    public FromScratch setAttribute(final JsonPointer attributePath, final JsonValue attributeValue) {
        checkNotNull(attributeValue, "attribute value to be set");
        invokeOnAttributesBuilder(ab -> ab.set(attributePath, attributeValue));
        return this;
    }

    @Override
    public FromScratch removeAttribute(final JsonPointer attributePath) {
        if (null != attributesBuilder) {
            invokeOnAttributesBuilder(ab -> ab.remove(attributePath));
        }
        return this;
    }

    @Override
    public FromScratch setDefinition(@Nullable final ThingDefinition definition) {
        this.definition = definition;
        return this;
    }

    @Override
    public FromScratch setNullDefinition() {
        this.definition = NullThingDefinition.getInstance();
        return this;
    }

    @Override
    public FromScratch removeDefinition() {
        definition = null;
        return this;
    }

    @Override
    public FromScratch setFeature(final Feature feature) {
        invokeOnFeaturesBuilder(fb -> fb.set(feature));
        return this;
    }

    @Override
    public FromScratch setFeature(final String featureId) {
        return setFeature(ThingsModelFactory.newFeature(featureId));
    }

    @Override
    public FromScratch setFeature(final String featureId, @Nullable final FeatureDefinition featureDefinition,
            final FeatureProperties featureProperties) {

        return setFeature(ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties));
    }

    @Override
    public FromScratch setFeature(final CharSequence featureId, @Nullable final FeatureDefinition featureDefinition,
            @Nullable final FeatureProperties featureProperties,
            @Nullable final FeatureProperties featureDesiredProperties) {

        return setFeature(ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties,
                featureDesiredProperties));
    }

    @Override
    public FromScratch setFeature(final String featureId, @Nullable final FeatureProperties featureProperties) {
        return setFeature(ThingsModelFactory.newFeature(featureId, featureProperties));
    }

    @Override
    public FromScratch removeFeature(final String featureId) {
        invokeOnFeaturesBuilder(fb -> fb.remove(featureId));
        final Features fs = getFeatures();
        if (null == fs || fs.isEmpty()) {
            featuresBuilder = null;
        }
        return this;
    }

    @Override
    public FromScratch setFeatureDefinition(final String featureId,
            @Nullable final FeatureDefinition featureDefinition) {
        checkNotNull(featureDefinition, "Feature Definition to be set");
        invokeOnFeaturesBuilder(fb -> fb.set(fb.get(featureId)
                .map(feature -> feature.setDefinition(featureDefinition))
                .orElseGet(() -> ThingsModelFactory.newFeature(featureId, featureDefinition))));
        return this;
    }

    @Override
    public FromScratch removeFeatureDefinition(final String featureId) {
        if (null != featuresBuilder) {
            featuresBuilder.get(featureId)
                    .map(Feature::removeDefinition)
                    .ifPresent(featuresBuilder::set);
        }
        return this;
    }

    @Override
    public FromScratch setFeatureProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue propertyValue) {

        checkNotNull(propertyValue, "property value to be set");

        final Features existingFeatures = getFeatures();
        if (null != existingFeatures) {
            return setFeatures(existingFeatures.setProperty(featureId, propertyPath, propertyValue));
        } else {
            final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(propertyPath, propertyValue)
                    .build();
            return setFeature(featureId, featureProperties);
        }
    }

    @Override
    public FromScratch removeFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        checkNotNull(featureId, "identifier of the Feature from which the property to be removed");
        checkNotNull(propertyPath, "path to the property to be removed");

        if (null != featuresBuilder) {
            final Features existingFeatures = getFeatures();
            if (null != existingFeatures) {
                return setFeatures(existingFeatures.removeProperty(featureId, propertyPath));
            }
        }
        return this;
    }

    @Override
    public FromScratch setFeatureProperties(final String featureId, final FeatureProperties featureProperties) {
        checkNotNull(featureId, "ID of the Feature to set the properties for");
        checkNotNull(featureProperties, "FeatureProperties to be set");

        invokeOnFeaturesBuilder(fb -> fb.set(fb.get(featureId)
                .map(feature -> feature.setProperties(featureProperties))
                .orElseGet(() -> ThingsModelFactory.newFeature(featureId, featureProperties))));
        return this;
    }

    @Override
    public FromScratch removeFeatureProperties(final String featureId) {
        checkNotNull(featureId, "ID of the Feature to set the properties for");
        if (null != featuresBuilder) {
            featuresBuilder.get(featureId)
                    .map(Feature::removeProperties)
                    .ifPresent(featuresBuilder::set);
        }
        return this;
    }

    @Override
    public FromScratch setFeatureDesiredProperty(final CharSequence featureId, final JsonPointer desiredPropertyPath,
            final JsonValue desiredPropertyValue) {

        checkNotNull(desiredPropertyPath, "desiredPropertyPath");

        final Features existingFeatures = getFeatures();
        if (null != existingFeatures) {
            return setFeatures(
                    existingFeatures.setDesiredProperty(featureId, desiredPropertyPath, desiredPropertyValue));
        } else {
            final FeatureProperties featureDesiredProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(desiredPropertyPath, desiredPropertyValue)
                    .build();
            return setFeature(featureId, null, null, featureDesiredProperties);
        }
    }

    @Override
    public FromScratch removeFeatureDesiredProperty(final CharSequence featureId,
            final JsonPointer desiredPropertyPath) {
        checkNotNull(featureId, "featureId");
        checkNotNull(desiredPropertyPath, "desiredPropertyPath");

        if (null != featuresBuilder) {
            final Features existingFeatures = getFeatures();
            if (null != existingFeatures) {
                return setFeatures(existingFeatures.removeDesiredProperty(featureId, desiredPropertyPath));
            }
        }
        return this;
    }

    @Override
    public FromScratch setFeatureDesiredProperties(final CharSequence featureId,
            final FeatureProperties desiredPropertiesPath) {
        checkNotNull(featureId, "featureId");
        checkNotNull(desiredPropertiesPath, "desiredPropertiesPath");

        invokeOnFeaturesBuilder(fb -> fb.set(fb.get(featureId)
                .map(feature -> feature.setDesiredProperties(desiredPropertiesPath))
                .orElseGet(() -> ThingsModelFactory.newFeature(featureId, null, null,
                        desiredPropertiesPath))));
        return this;
    }

    @Override
    public FromScratch removeFeatureDesiredProperties(final CharSequence featureId) {
        checkNotNull(featureId, "featureId");
        if (null != featuresBuilder) {
            featuresBuilder.get(featureId)
                    .map(Feature::removeDesiredProperties)
                    .ifPresent(featuresBuilder::set);
        }
        return this;
    }

    @Override
    public FromScratch setFeatures(final Iterable<Feature> features) {
        checkNotNull(features, "Features to be set");

        if (features instanceof Features) {
            final Features featuresToSet = (Features) features;
            if (featuresToSet.isNull()) {
                return setNullFeatures();
            }
        }

        invokeOnFeaturesBuilder(fb -> fb.removeAll().setAll(features));
        return this;
    }

    @Override
    public FromScratch removeAllFeatures() {
        featuresBuilder = null;
        return this;
    }

    @Override
    public FromScratch setEmptyFeatures() {
        featuresBuilder = null;
        features = ThingsModelFactory.emptyFeatures();
        return this;
    }

    @Override
    public FromScratch setNullFeatures() {
        featuresBuilder = null;
        features = ThingsModelFactory.nullFeatures();
        return this;
    }

    @Override
    public FromScratch setFeatures(final JsonObject featuresJsonObject) {
        checkNotNull(featuresJsonObject, "JSON object representation of Features to be set");

        if (featuresJsonObject.isNull()) {
            return setNullFeatures();
        }
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonObject));
    }

    @Override
    public FromScratch setFeatures(final String featuresJsonString) {
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonString));
    }

    @Override
    public FromScratch setLifecycle(@Nullable final ThingLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    @Override
    public FromScratch setRevision(@Nullable final ThingRevision revision) {
        this.revision = revision;
        return this;
    }

    @Override
    public FromScratch setRevision(final long revisionNumber) {
        return setRevision(ThingsModelFactory.newThingRevision(revisionNumber));
    }

    @Override
    public FromScratch setModified(@Nullable final Instant modified) {
        this.modified = modified;
        return this;
    }

    @Override
    public FromScratch setCreated(@Nullable final Instant created) {
        this.created = created;
        return this;
    }

    @Override
    public FromScratch setMetadata(@Nullable final Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    @Override
    public FromScratch setPolicyId(@Nullable final PolicyId policyId) {
        this.policyId = policyId;
        return this;
    }

    @Override
    public FromScratch removePolicyId() {
        policyId = null;
        return this;
    }

    @Override
    public FromScratch setId(@Nullable final ThingId thingId) {
        id = thingId;
        return this;
    }

    @Override
    public FromScratch setGeneratedId() {
        id = ThingBuilder.generateRandomTypedThingId();
        return this;
    }

    @Override
    public Thing build() {
        return ImmutableThing.of(id, policyId, definition, getAttributes(), getFeatures(), lifecycle,
                revision, modified, created, metadata);
    }

    private void invokeOnAttributesBuilder(final Consumer<AttributesBuilder> attributesBuilderConsumer) {
        AttributesBuilder result = attributesBuilder;
        if (null == result) {
            result = ThingsModelFactory.newAttributesBuilder();
            attributesBuilder = result;
        }
        attributesBuilderConsumer.accept(result);
        attributes = null;
    }

    private void invokeOnFeaturesBuilder(final Consumer<FeaturesBuilder> featuresBuilderConsumer) {
        FeaturesBuilder result = featuresBuilder;
        if (null == result) {
            result = ThingsModelFactory.newFeaturesBuilder();
            featuresBuilder = result;
        }
        featuresBuilderConsumer.accept(result);
        features = null;
    }

    @Nullable
    Attributes getAttributes() {
        Attributes result = attributes;
        if (null != attributesBuilder) {
            result = attributesBuilder.build();
        }
        return result;
    }

    @Nullable
    Features getFeatures() {
        Features result = features;
        if (null != featuresBuilder) {
            result = featuresBuilder.build();
        }
        return result;
    }

}
