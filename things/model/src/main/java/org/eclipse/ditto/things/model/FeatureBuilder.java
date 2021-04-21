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

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;


/**
 * A mutable builder for for a {@link Feature} which uses Object Scoping and Method Chaining to provide a fluent API.
 */
public interface FeatureBuilder {

    /**
     * This interface eventually allows create a new {@link Feature} object.
     */
    interface FeatureBuildable {

        /**
         * Creates a new {@link Feature} object based on the data which were set to this builder beforehand.
         *
         * @return a new Feature object.
         */
        Feature build();
    }

    /**
     * This interface enables the creation of a {@link Feature} object from JSON.
     */
    interface FromJsonBuildable {

        /**
         * Sets the provided Feature ID.
         *
         * @param featureId the ID to use for the Feature to be created.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         * @throws IllegalArgumentException if {@code featureId} is empty.
         */
        FeatureBuildable useId(String featureId);
    }

    /**
     * This interface enables the creation of a {@link Feature} object from scratch.
     */
    interface FromScratchBuildable {

        /**
         * Sets the specified Definition to the Feature to be built.
         *
         * @param featureDefinition the Definition to be set or {@code null}.
         * @return this builder to allow method chaining.
         */
        FromScratchBuildable definition(@Nullable FeatureDefinition featureDefinition);

        /**
         * Sets the properties of the Feature.
         *
         * @param properties the properties of the Feature to be created or {@code null}
         * @return this builder to allow method chaining.
         */
        FromScratchBuildable properties(@Nullable FeatureProperties properties);

        /**
         * Sets the properties of the Feature.
         *
         * @param properties the properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         */
        FromScratchBuildable properties(@Nullable JsonObject properties);

        /**
         * Sets the desired properties of the Feature.
         *
         * @param desiredProperties the desired properties of the Feature to be created or {@code null}
         * @return this builder to allow method chaining.
         * @since 1.5.0
         */
        FromScratchBuildable desiredProperties(@Nullable FeatureProperties desiredProperties);

        /**
         * Sets the desired properties of the Feature.
         *
         * @param desiredProperties the desired properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         * @since 1.5.0
         */
        FromScratchBuildable desiredProperties(@Nullable JsonObject desiredProperties);

        /**
         * Sets the provided ID instead of the one which was possibly contained in the Feature's JSON.
         *
         * @param featureId the ID to use in the Feature to be created.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         * @throws IllegalArgumentException if {@code featureId} is empty.
         */
        FeatureBuildable withId(String featureId);
    }

    /**
     * A mutable builder with a fluent API for an immutable {@link Feature}. This builder is initialised with the
     * properties of an existing Feature.
     */
    @NotThreadSafe
    interface FromCopyBuildable {

        /**
         * Sets the specified Definition to the Feature to be built.
         *
         * @param featureDefinition the Definition to be set or {@code null}.
         * @return this builder to allow method chaining.
         */
        FromCopyBuildable definition(@Nullable FeatureDefinition featureDefinition);

        /**
         * Sets the properties of the Feature.
         *
         * @param properties the properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         */
        FromCopyBuildable properties(@Nullable FeatureProperties properties);

        /**
         * Sets the properties of the Feature.
         *
         * @param properties the properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         */
        FromCopyBuildable properties(@Nullable JsonObject properties);

        /**
         * Calls the given {@code transform} function with the currently set properties of this builder. The result of
         * the {@code transform} function is set as new properties of the Feature to be built.
         *
         * @param transform the function to transform the current properties of the Feature to be created. If there are
         * no properties set yet, the function is called with empty properties.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code transform} is {@code null}.
         */
        FromCopyBuildable properties(Function<FeatureProperties, FeatureProperties> transform);

        /**
         * Sets the desired properties of the Feature.
         *
         * @param desiredProperties the desired properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         * @since 1.5.0
         */
        FromCopyBuildable desiredProperties(@Nullable FeatureProperties desiredProperties);

        /**
         * Sets the desired properties of the Feature.
         *
         * @param desiredProperties the desired properties of the Feature to be created or {@code null}.
         * @return this builder to allow method chaining.
         * @since 1.5.0
         */
        FromCopyBuildable desiredProperties(@Nullable JsonObject desiredProperties);

        /**
         * Calls the given {@code transform} function with the currently set desired properties of this builder.
         * The result of the {@code transform} function is set as new desired properties of the Feature to be built.
         *
         * @param transform the function to transform the current desired properties of the Feature to be created.
         * If there are no desired properties set yet, the function is called with empty desired properties.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code transform} is {@code null}.
         * @since 1.5.0
         */
        FromCopyBuildable desiredProperties(UnaryOperator<FeatureProperties> transform);

        /**
         * Sets the given Feature ID to this builder.
         *
         * @param featureId the Feature ID to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code featureId} is {@code null}.
         * @throws IllegalArgumentException if {@code featureId} is empty.
         */
        default FromCopyBuildable setId(final String featureId) {
            return setId(existingId -> true, featureId);
        }

        /**
         * Sets the given Feature ID to this builder.
         *
         * @param existingIdPredicate a predicate to decide whether the given ID is set. The predicate receives
         * the currently set Feature ID.
         * @param featureId the Feature ID to be set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code featureId} is empty.
         */
        FromCopyBuildable setId(Predicate<String> existingIdPredicate, String featureId);

        /**
         * Creates a new Feature object based on the data which was provided to this builder.
         *
         * @return a new Feature object.
         */
        Feature build();
    }

}
