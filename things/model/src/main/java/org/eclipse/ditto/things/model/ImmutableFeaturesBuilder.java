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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder for an {@link ImmutableFeatures} with a fluent API.
 */
@NotThreadSafe
final class ImmutableFeaturesBuilder implements FeaturesBuilder {

    private final Map<String, Feature> features;

    private ImmutableFeaturesBuilder(final Map<String, Feature> theFeatures) {
        features = theFeatures;
    }

    public static FeaturesBuilder newInstance() {
        return new ImmutableFeaturesBuilder(new LinkedHashMap<>());
    }

    @Override
    public FeaturesBuilder set(final Feature feature) {
        checkNotNull(feature, "feature to be set");

        features.put(feature.getId(), feature);

        return this;
    }

    @Override
    public Optional<Feature> get(final CharSequence featureId) {
        checkNotNull(featureId, "ID of the Feature to be returned");
        return Optional.ofNullable(features.get(featureId.toString()));
    }

    @Override
    public FeaturesBuilder setAll(final Iterable<Feature> features) {
        checkNotNull(features, "features to be set");

        features.forEach(f -> this.features.put(f.getId(), f));

        return this;
    }

    @Override
    public FeaturesBuilder remove(final Feature feature) {
        checkNotNull(feature, "feature to be removed");

        features.remove(feature.getId());

        return this;
    }

    @Override
    public FeaturesBuilder remove(final String featureId) {
        checkNotNull(featureId, "identifier of the feature to be removed");

        features.remove(featureId);

        return this;
    }

    @Override
    public FeaturesBuilder removeAll() {
        features.clear();
        return this;
    }

    @Override
    public Features build() {
        return ImmutableFeatures.of(features.values());
    }

    @Override
    public Iterator<Feature> iterator() {
        return features.values().iterator();
    }

}
