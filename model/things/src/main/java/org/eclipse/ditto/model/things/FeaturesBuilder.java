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

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder for a {@link Feature} with a fluent API.
 */
@NotThreadSafe
public interface FeaturesBuilder extends Iterable<Feature> {

    /**
     * Sets the given Feature to this builder. A previously set Feature with the same ID is replaced.
     *
     * @param feature the Feature to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    FeaturesBuilder set(Feature feature);

    /**
     * Returns the Feature with the specified identifier from this builder or an empty Optional if such a Feature is
     * unknown to this builder.
     *
     * @param featureId the identifier of the Feature to be returned.
     * @return the Feature with ID {@code featureId} or an empty Optional.
     */
    Optional<Feature> get(CharSequence featureId);

    /**
     * Sets all given Features to this builder. Each previously set feature with the same ID is replaced.
     *
     * @param features the Features to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    FeaturesBuilder setAll(Iterable<Feature> features);

    /**
     * Removes the given Feature from this builder. Same like {@link #remove(String)}.
     *
     * @param feature the Feature to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    FeaturesBuilder remove(Feature feature);

    /**
     * Removes the Feature with the given ID from this builder.
     *
     * @param featureId the ID of the Feature to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    FeaturesBuilder remove(String featureId);

    /**
     * Removes all previously set Features.
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
