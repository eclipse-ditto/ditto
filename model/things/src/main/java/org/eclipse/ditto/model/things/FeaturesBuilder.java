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

/**
 * A mutable builder for a {@link Feature} with a fluent API.
 */
@NotThreadSafe
public interface FeaturesBuilder {

    /**
     * Sets the given feature to this builder. A previously set feature with the same identifier is replaced.
     *
     * @param feature the feature to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    FeaturesBuilder set(Feature feature);

    /**
     * Sets all given features to this builder. Each previously set feature with the same identifier is replaced.
     *
     * @param features the features to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    FeaturesBuilder setAll(Iterable<Feature> features);

    /**
     * Removes the given feature from this builder. Same like {@link #remove(String)}.
     *
     * @param feature the feature to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    FeaturesBuilder remove(Feature feature);

    /**
     * Removes the feature with the given identifier from this builder.
     *
     * @param featureId the identifier of the feature to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    FeaturesBuilder remove(String featureId);

    /**
     * Removes all previously set features.
     *
     * @return this builder to allow method chaining.
     */
    FeaturesBuilder removeAll();

    /**
     * Creates a new {@link Features} object containing all features which were set to this builder beforehand.
     *
     * @return the new {@code Features} object.
     */
    Features build();

}
