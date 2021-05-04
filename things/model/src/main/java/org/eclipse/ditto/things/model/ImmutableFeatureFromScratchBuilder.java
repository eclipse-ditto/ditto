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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * A mutable builder for a {@link Feature} with a fluent API.
 */
@NotThreadSafe
final class ImmutableFeatureFromScratchBuilder implements FeatureBuilder, FeatureBuilder.FromJsonBuildable,
        FeatureBuilder.FromScratchBuildable, FeatureBuilder.FeatureBuildable {

    private String featureId;
    @Nullable private FeatureDefinition definition;
    @Nullable private FeatureProperties properties;
    @Nullable private FeatureProperties desiredProperties;
    private boolean isFeatureValueJsonNull;

    private ImmutableFeatureFromScratchBuilder() {
        featureId = "emptyFeatureId";
        properties = null;
        desiredProperties = null;
        definition = null;
        isFeatureValueJsonNull = false;
    }

    /**
     * Creates a new builder for a Feature object based on the given JSON object.
     *
     * @param jsonObject the JSON object of which a new Feature instance is to be created.
     * @return an object handle which allows further configuration of the Feature to be built.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FromJsonBuildable newFeatureFromJson(final JsonObject jsonObject) {
        ConditionChecker.checkNotNull(jsonObject, "Feature JSON object");

        final ImmutableFeatureFromScratchBuilder result = new ImmutableFeatureFromScratchBuilder();
        if (jsonObject.isNull()) {
            result.isFeatureValueJsonNull = true;
        } else {
            result.definition(jsonObject.getValue(Feature.JsonFields.DEFINITION)
                    .map(ThingsModelFactory::newFeatureDefinition)
                    .orElse(null));
            result.properties(jsonObject.getValue(Feature.JsonFields.PROPERTIES)
                    .map(ThingsModelFactory::newFeatureProperties)
                    .orElse(null));
            result.desiredProperties(jsonObject.getValue(Feature.JsonFields.DESIRED_PROPERTIES)
                    .map(ThingsModelFactory::newFeatureProperties)
                    .orElse(null));
        }

        return result;
    }

    /**
     * Creates a new builder for a Feature object from scratch.
     *
     * @return an object handle which allows further configuration of the Feature to be built.
     */
    public static FromScratchBuildable newFeatureFromScratch() {
        return new ImmutableFeatureFromScratchBuilder();
    }

    @Override
    public FeatureBuildable useId(final String featureId) {
        ConditionChecker.checkNotNull(featureId, "Feature ID");
        ConditionChecker.checkArgument(featureId, s -> !s.isEmpty(), () -> "The Feature ID must not be empty!");

        this.featureId = featureId;
        return this;
    }

    @Override
    public FromScratchBuildable definition(@Nullable final FeatureDefinition featureDefinition) {
        definition = featureDefinition;
        return this;
    }

    @Override
    public FromScratchBuildable properties(@Nullable final FeatureProperties properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public FromScratchBuildable properties(@Nullable final JsonObject properties) {
        if (null == properties) {
            this.properties = null;
        } else {
            this.properties = properties instanceof FeatureProperties
                    ? (FeatureProperties) properties
                    : ThingsModelFactory.newFeatureProperties(properties);
        }
        return this;
    }

    @Override
    public FromScratchBuildable desiredProperties(@Nullable final FeatureProperties desiredProperties) {
        this.desiredProperties = desiredProperties;
        return this;
    }

    @Override
    public FromScratchBuildable desiredProperties(@Nullable final JsonObject desiredProperties) {
        if (null == desiredProperties) {
            this.desiredProperties = null;
        } else {
            this.desiredProperties = desiredProperties instanceof FeatureProperties
                    ? (FeatureProperties) desiredProperties
                    : ThingsModelFactory.newFeatureProperties(desiredProperties);
        }
        return this;
    }

    @Override
    public FeatureBuildable withId(final String featureId) {
        return useId(featureId);
    }

    @Override
    public Feature build() {
        if (isFeatureValueJsonNull) {
            return ThingsModelFactory.nullFeature(featureId);
        } else {
            return ThingsModelFactory.newFeature(featureId, definition, properties, desiredProperties);
        }
    }

}
