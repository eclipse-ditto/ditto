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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * IconLink is a {@link BaseLink} being of {@code rel="icon"}.
 *
 * @since 2.4.0
 */
public interface IconLink extends BaseLink<IconLink> {

    /**
     * Creates a new IconLink from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the icon link.
     * @return the IconLink.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static IconLink fromJson(final JsonObject jsonObject) {
        return new ImmutableIconLink(jsonObject);
    }

    /**
     * Creates a new builder for building an IconLink.
     *
     * @return the builder.
     */
    static IconLink.Builder newBuilder() {
        return IconLink.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an IconLink, initialized with the values from the specified JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static IconLink.Builder newBuilder(final JsonObject jsonObject) {
        return IconLink.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns the optional sizes attribute specifying icon dimensions (e.g., "16x16", "32x32 64x64").
     *
     * @return the optional sizes.
     */
    Optional<String> getSizes();

    @Override
    default IconLink.Builder toBuilder() {
        return IconLink.Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building an {@link IconLink}.
     */
    interface Builder extends BaseLink.Builder<Builder, IconLink> {

        /**
         * Creates a new builder for building an IconLink.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableIconLinkBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an IconLink, initialized with the values from the specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableIconLinkBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the sizes attribute for the icon.
         *
         * @param sizes the sizes (e.g., "16x16"), or {@code null} to remove.
         * @return this builder.
         */
        Builder setSizes(@Nullable String sizes);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an IconLink.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the icon sizes.
         */
        public static final JsonFieldDefinition<String> SIZES = JsonFactory.newStringFieldDefinition(
                "sizes");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
