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

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link ArraySchema}.
 */
@Immutable
final class ImmutableArraySchema extends AbstractSingleDataSchema implements ArraySchema {

    ImmutableArraySchema(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.ARRAY);
    }

    @Override
    public Optional<DataSchema> getItems() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.ITEMS_MULTIPLE)
                        .map(MultipleDataSchema::fromJson)
                        .map(DataSchema.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.ITEMS)
                                .map(SingleDataSchema::fromJson)
                                .orElse(null))
        );
    }

    @Override
    public Optional<Integer> getMinItems() {
        return wrappedObject.getValue(ArraySchema.JsonFields.MIN_ITEMS);
    }

    @Override
    public Optional<Integer> getMaxItems() {
        return wrappedObject.getValue(ArraySchema.JsonFields.MAX_ITEMS);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableArraySchema;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
