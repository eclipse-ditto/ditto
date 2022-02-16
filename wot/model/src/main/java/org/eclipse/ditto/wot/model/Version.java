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
 * A Version provides version information, e.g. of {@code model} or {@code instance}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#versioninfo">WoT TD VersionInfo</a>
 * @since 2.4.0
 */
public interface Version extends Jsonifiable<JsonObject> {

    static Version fromJson(final JsonObject jsonObject) {
        return new ImmutableVersion(jsonObject);
    }

    static Version.Builder newBuilder() {
        return Version.Builder.newBuilder();
    }

    static Version.Builder newBuilder(final JsonObject jsonObject) {
        return Version.Builder.newBuilder(jsonObject);
    }

    default Version.Builder toBuilder() {
        return Version.Builder.newBuilder(toJson());
    }

    Optional<String> getInstance();

    Optional<String> getModel();

    interface Builder {

        static Builder newBuilder() {
            return new MutableVersionBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableVersionBuilder(jsonObject.toBuilder());
        }

        Builder setInstance(String instance);

        Builder setModel(String model);

        Version build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Version.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> INSTANCE = JsonFactory.newStringFieldDefinition(
                "instance");

        public static final JsonFieldDefinition<String> MODEL = JsonFactory.newStringFieldDefinition(
                "model");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
