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

import org.eclipse.ditto.json.JsonObject;

/**
 * A NullSchema is a {@link SingleDataSchema} describing the JSON {@code null}.
 *
 * @since 2.4.0
 */
public interface NullSchema extends SingleDataSchema {

    static NullSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableNullSchema(jsonObject);
    }

    static NullSchema.Builder newBuilder() {
        return NullSchema.Builder.newBuilder();
    }

    static NullSchema.Builder newBuilder(final JsonObject jsonObject) {
        return NullSchema.Builder.newBuilder(jsonObject);
    }

    default NullSchema.Builder toBuilder() {
        return NullSchema.Builder.newBuilder(toJson());
    }

    interface Builder extends SingleDataSchema.Builder<Builder, NullSchema> {

        static Builder newBuilder() {
            return new MutableNullSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableNullSchemaBuilder(jsonObject.toBuilder());
        }

    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.NULL);
    }
}
