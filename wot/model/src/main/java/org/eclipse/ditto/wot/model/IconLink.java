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

    static IconLink fromJson(final JsonObject jsonObject) {
        return new ImmutableIconLink(jsonObject);
    }

    static IconLink.Builder newBuilder() {
        return IconLink.Builder.newBuilder();
    }

    static IconLink.Builder newBuilder(final JsonObject jsonObject) {
        return IconLink.Builder.newBuilder(jsonObject);
    }

    Optional<String> getSizes();

    @Override
    default IconLink.Builder toBuilder() {
        return IconLink.Builder.newBuilder(toJson());
    }

    interface Builder extends BaseLink.Builder<Builder, IconLink> {

        static Builder newBuilder() {
            return new MutableIconLinkBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableIconLinkBuilder(jsonObject.toBuilder());
        }

        Builder setSizes(@Nullable String sizes);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an IconLink.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> SIZES = JsonFactory.newStringFieldDefinition(
                "sizes");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
