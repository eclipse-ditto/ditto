/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * The Thing Model (TM) provides a reusable template for Thing Descriptions.
 * <p>
 * Thing Models define the data model definitions within Things' {@link Properties},
 * {@link Actions}, and/or {@link Events} and can be used as templates for creating
 * concrete {@link ThingDescription} instances. Unlike Thing Descriptions, Thing Models
 * are not required to have an {@code id} and may contain placeholders.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#introduction-tm">WoT TD Thing Model</a>
 * @since 2.4.0
 */
public interface ThingModel extends ThingSkeleton<ThingModel> {

    /**
     * Creates a new ThingModel from the specified JSON object.
     *
     * @param jsonObject the JSON object representing a Thing Model.
     * @return the ThingModel.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ThingModel fromJson(final JsonObject jsonObject) {
        return new ImmutableThingModel(jsonObject);
    }

    /**
     * Creates a new builder for building a ThingModel.
     *
     * @return the builder.
     */
    static ThingModel.Builder newBuilder() {
        return ThingModel.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a ThingModel, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ThingModel.Builder newBuilder(final JsonObject jsonObject) {
        return ThingModel.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns the optional list of JSON Pointers identifying affordances that are optional in implementations
     * derived from this Thing Model.
     *
     * @return the optional list of optional affordance pointers.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing-model">WoT TD Thing Model (tm:optional)</a>
     */
    Optional<TmOptional> getTmOptional();

    /**
     * Returns a mutable builder with a fluent API for building a ThingModel, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default ThingModel.Builder toBuilder() {
        return ThingModel.Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building a {@link ThingModel}.
     */
    interface Builder extends ThingSkeletonBuilder<Builder, ThingModel> {

        /**
         * Creates a new builder for building a ThingModel.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableThingModelBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a ThingModel, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableThingModelBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the list of optional affordance pointers.
         *
         * @param tmOptional the optional affordances, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing-model">WoT TD Thing Model (tm:optional)</a>
         */
        Builder setTmOptional(@Nullable TmOptional tmOptional);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a ThingModel.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the array of optional affordance pointers.
         */
        public static final JsonFieldDefinition<JsonArray> TM_OPTIONAL = JsonFactory.newJsonArrayFieldDefinition(
                "tm:optional");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
