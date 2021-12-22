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
 * A BooleanSchema is a {@link SingleDataSchema} describing the JSON data type {@code boolean}.
 *
 * @since 2.4.0
 */
public interface BooleanSchema extends SingleDataSchema {

    static BooleanSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableBooleanSchema(jsonObject);
    }

    static BooleanSchema.Builder newBuilder() {
        return BooleanSchema.Builder.newBuilder();
    }

    static BooleanSchema.Builder newBuilder(final JsonObject jsonObject) {
        return BooleanSchema.Builder.newBuilder(jsonObject);
    }

    default BooleanSchema.Builder toBuilder() {
        return BooleanSchema.Builder.newBuilder(toJson());
    }

    interface Builder extends SingleDataSchema.Builder<Builder, BooleanSchema> {

        static Builder newBuilder() {
            return new MutableBooleanSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableBooleanSchemaBuilder(jsonObject.toBuilder());
        }

    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.BOOLEAN);
    }
}
