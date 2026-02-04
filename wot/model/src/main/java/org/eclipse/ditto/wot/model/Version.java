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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A Version provides version information about the Thing Description or Thing Model.
 * <p>
 * Version information can include both the model version (template version) and the instance version
 * (version of the specific Thing).
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#versioninfo">WoT TD VersionInfo</a>
 * @since 2.4.0
 */
public interface Version extends Jsonifiable<JsonObject> {

    /**
     * Creates a new Version from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the version info.
     * @return the Version.
     */
    static Version fromJson(final JsonObject jsonObject) {
        return new ImmutableVersion(jsonObject);
    }

    /**
     * Creates a new builder for building a Version.
     *
     * @return the builder.
     */
    static Version.Builder newBuilder() {
        return Version.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a Version, initialized with the values from the specified JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static Version.Builder newBuilder(final JsonObject jsonObject) {
        return Version.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a Version, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default Version.Builder toBuilder() {
        return Version.Builder.newBuilder(toJson());
    }

    /**
     * Returns the optional instance version, which represents the version of the specific Thing instance.
     *
     * @return the optional instance version.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#versioninfo">WoT TD VersionInfo (instance)</a>
     */
    Optional<String> getInstance();

    /**
     * Returns the optional model version, which represents the version of the Thing Model this description is based on.
     *
     * @return the optional model version.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#versioninfo">WoT TD VersionInfo (model)</a>
     */
    Optional<String> getModel();

    /**
     * A mutable builder with a fluent API for building a {@link Version}.
     */
    interface Builder {

        /**
         * Creates a new builder for building a Version.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableVersionBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a Version, initialized with the values from the specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableVersionBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the instance version.
         *
         * @param instance the instance version.
         * @return this builder.
         */
        Builder setInstance(String instance);

        /**
         * Sets the model version.
         *
         * @param model the model version.
         * @return this builder.
         */
        Builder setModel(String model);

        /**
         * Builds the Version.
         *
         * @return the built Version instance.
         */
        Version build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Version.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the instance version.
         */
        public static final JsonFieldDefinition<String> INSTANCE = JsonFactory.newStringFieldDefinition(
                "instance");

        /**
         * JSON field definition for the model version.
         */
        public static final JsonFieldDefinition<String> MODEL = JsonFactory.newStringFieldDefinition(
                "model");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
