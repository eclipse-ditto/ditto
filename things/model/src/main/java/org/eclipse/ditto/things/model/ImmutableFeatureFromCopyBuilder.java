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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;

/**
 * A mutable builder with a fluent API for an immutable {@link Feature}. This builder is initialised with the
 * properties of an existing Feature.
 */
@NotThreadSafe
final class ImmutableFeatureFromCopyBuilder implements FeatureBuilder, FeatureBuilder.FromCopyBuildable {

    private String featureId;
    @Nullable private FeatureDefinition definition;
    @Nullable private FeatureProperties properties;
    @Nullable private FeatureProperties desiredProperties;

    private ImmutableFeatureFromCopyBuilder(final String theFeatureId) {
        featureId = theFeatureId;
        properties = null;
        desiredProperties = null;
        definition = null;
    }

    /**
     * Returns a new {@code ImmutableFeatureFromCopyBuilder} which is initialised with the properties of the given
     * Feature.
     *
     * @param feature an existing Feature which provides the properties of the new Feature.
     * @return the new builder.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    public static ImmutableFeatureFromCopyBuilder of(final Feature feature) {
        checkNotNull(feature, "Feature");

        final ImmutableFeatureFromCopyBuilder result = new ImmutableFeatureFromCopyBuilder(feature.getId());
        result.properties(feature.getProperties().orElse(null));
        result.desiredProperties(feature.getDesiredProperties().orElse(null));
        result.definition(feature.getDefinition().orElse(null));

        return result;
    }

    @Override
    public FromCopyBuildable definition(@Nullable final FeatureDefinition featureDefinition) {
        definition = featureDefinition;
        return this;
    }

    @Override
    public FromCopyBuildable properties(@Nullable final FeatureProperties properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public FromCopyBuildable properties(@Nullable final JsonObject properties) {
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
    public FromCopyBuildable properties(final Function<FeatureProperties, FeatureProperties> transform) {
        checkNotNull(transform, "transform function");

        properties = transform.apply(properties == null ? ThingsModelFactory.emptyFeatureProperties() : properties);
        return this;
    }

    @Override
    public FromCopyBuildable desiredProperties(@Nullable final FeatureProperties desiredProperties) {
        this.desiredProperties = desiredProperties;
        return this;
    }

    @Override
    public FromCopyBuildable desiredProperties(@Nullable final JsonObject desiredProperties) {
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
    public FromCopyBuildable desiredProperties(final UnaryOperator<FeatureProperties> transform) {
        checkNotNull(transform, "transform function");

        desiredProperties = transform.apply(desiredProperties == null ?
                ThingsModelFactory.emptyFeatureProperties() : desiredProperties);
        return this;
    }

    @Override
    public FromCopyBuildable setId(final String featureId) {
        this.featureId = argumentNotEmpty(featureId, "Feature ID to be set");
        return this;
    }

    @Override
    public FromCopyBuildable setId(final Predicate<String> existingIdPredicate, final String featureId) {
        if (existingIdPredicate.test(this.featureId)) {
            setId(featureId);
        }
        return this;
    }

    @Override
    public Feature build() {
        return ImmutableFeature.of(featureId, definition, properties, desiredProperties);
    }

}
