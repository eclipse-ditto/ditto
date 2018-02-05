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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Iterator;
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
        return new ImmutableFeaturesBuilder(new HashMap<>());
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
