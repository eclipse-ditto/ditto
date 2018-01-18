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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;
import java.util.function.Predicate;

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

    private ImmutableFeatureFromCopyBuilder(final String theFeatureId) {
        featureId = theFeatureId;
        properties = null;
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
        return ImmutableFeature.of(featureId, definition, properties);
    }

}
