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
 * Immutable implementation of {@link IntegerSchema}.
 */
@Immutable
final class ImmutableIntegerSchema extends AbstractSingleDataSchema implements IntegerSchema {

    ImmutableIntegerSchema(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.INTEGER);
    }

    @Override
    public Optional<Integer> getMinimum() {
        return wrappedObject.getValue(IntegerSchema.JsonFields.MINIMUM);
    }

    @Override
    public Optional<Integer> getExclusiveMinimum() {
        return wrappedObject.getValue(IntegerSchema.JsonFields.EXCLUSIVE_MINIMUM);
    }

    @Override
    public Optional<Integer> getMaximum() {
        return wrappedObject.getValue(IntegerSchema.JsonFields.MAXIMUM);
    }

    @Override
    public Optional<Integer> getExclusiveMaximum() {
        return wrappedObject.getValue(IntegerSchema.JsonFields.EXCLUSIVE_MAXIMUM);
    }

    @Override
    public Optional<Integer> getMultipleOf() {
        return wrappedObject.getValue(IntegerSchema.JsonFields.MULTIPLE_OF);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableIntegerSchema;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
