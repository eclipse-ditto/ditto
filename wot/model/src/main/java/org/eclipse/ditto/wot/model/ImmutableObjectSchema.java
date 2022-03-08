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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.JsonValueContainer;

/**
 * Immutable implementation of {@link ObjectSchema}.
 */
@Immutable
final class ImmutableObjectSchema extends AbstractSingleDataSchema implements ObjectSchema {

    ImmutableObjectSchema(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.OBJECT);
    }

    @Override
    public Map<String, SingleDataSchema> getProperties() {
        return wrappedObject.getValue(ObjectSchema.JsonFields.PROPERTIES)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(JsonValueContainer::stream)
                .orElse(Stream.empty())
                .collect(Collectors.toMap(
                        f -> f.getKey().toString(),
                        f -> SingleDataSchema.fromJson(f.getValue().asObject()),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new
                ));
    }

    @Override
    public List<String> getRequired() {
        return wrappedObject.getValue(ObjectSchema.JsonFields.REQUIRED)
                .map(JsonValueContainer::stream)
                .orElse(Stream.empty())
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toList());
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableObjectSchema;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
