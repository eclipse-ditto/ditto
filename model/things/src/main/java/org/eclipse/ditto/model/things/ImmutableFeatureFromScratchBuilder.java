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

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.ConditionChecker;

/**
 * A mutable builder for a {@link Feature} with a fluent API.
 */
@NotThreadSafe
final class ImmutableFeatureFromScratchBuilder implements FeatureBuilder, FeatureBuilder.FromJsonBuildable,
        FeatureBuilder.FromScratchBuildable, FeatureBuilder.FeatureBuildable {

    private String featureId;
    private FeatureProperties properties;
    private boolean isFeatureValueJsonNull;

    private ImmutableFeatureFromScratchBuilder() {
        featureId = null;
        properties = null;
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
        final ImmutableFeatureFromScratchBuilder featureBuilder = new ImmutableFeatureFromScratchBuilder();
        return featureBuilder.read(jsonObject);
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
    public FromScratchBuildable properties(final FeatureProperties properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public FromScratchBuildable properties(final JsonObject properties) {
        this.properties = properties instanceof FeatureProperties ? (FeatureProperties) properties
                : ThingsModelFactory.newFeatureProperties(properties);
        return this;
    }

    @Override
    public FeatureBuildable withId(final String featureId) {
        ConditionChecker.checkNotNull(featureId, "Feature ID");
        ConditionChecker.checkArgument(featureId, s -> !s.isEmpty(), () -> "The Feature ID must not be empty!");

        this.featureId = featureId;
        return this;
    }

    @Override
    public Feature build() {
        if (isFeatureValueJsonNull) {
            return ThingsModelFactory.nullFeature(featureId);
        } else {
            return ThingsModelFactory.newFeature(featureId, properties);
        }
    }

    private FromJsonBuildable read(final JsonObject jsonObject) {
        ConditionChecker.checkNotNull(jsonObject, "Feature JSON object");

        if (jsonObject.isNull()) {
            isFeatureValueJsonNull = true;
        } else {
            jsonObject.getValue(Feature.JsonFields.PROPERTIES)
                    .ifPresent(propertiesJsonObject -> properties =
                            ThingsModelFactory.newFeatureProperties(propertiesJsonObject));
        }

        return this;
    }

}
