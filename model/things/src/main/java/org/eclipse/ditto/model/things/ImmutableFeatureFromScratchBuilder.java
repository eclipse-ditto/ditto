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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.ConditionChecker;

/**
 * A mutable builder for a {@link Feature} with a fluent API.
 */
@NotThreadSafe
final class ImmutableFeatureFromScratchBuilder implements FeatureBuilder, FeatureBuilder.FromJsonBuildable,
        FeatureBuilder.FromScratchBuildable, FeatureBuilder.FeatureBuildable {

    @Nullable private String featureId;
    @Nullable private FeatureDefinition definition;
    @Nullable private FeatureProperties properties;
    private boolean isFeatureValueJsonNull;

    private ImmutableFeatureFromScratchBuilder() {
        featureId = null;
        properties = null;
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
    public FeatureBuildable withId(final String featureId) {
        return useId(featureId);
    }

    @Override
    public Feature build() {
        if (isFeatureValueJsonNull) {
            return ThingsModelFactory.nullFeature(featureId);
        } else {
            return ThingsModelFactory.newFeature(featureId, definition, properties);
        }
    }

}
