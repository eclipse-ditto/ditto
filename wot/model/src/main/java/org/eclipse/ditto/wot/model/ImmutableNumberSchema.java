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
 * Immutable implementation of {@link NumberSchema}.
 */
@Immutable
final class ImmutableNumberSchema extends AbstractSingleDataSchema implements NumberSchema {

    ImmutableNumberSchema(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.NUMBER);
    }

    @Override
    public Optional<Double> getMinimum() {
        return wrappedObject.getValue(NumberSchema.JsonFields.MINIMUM);
    }

    @Override
    public Optional<Double> getExclusiveMinimum() {
        return wrappedObject.getValue(NumberSchema.JsonFields.EXCLUSIVE_MINIMUM);
    }

    @Override
    public Optional<Double> getMaximum() {
        return wrappedObject.getValue(NumberSchema.JsonFields.MAXIMUM);
    }

    @Override
    public Optional<Double> getExclusiveMaximum() {
        return wrappedObject.getValue(NumberSchema.JsonFields.EXCLUSIVE_MAXIMUM);
    }

    @Override
    public Optional<Double> getMultipleOf() {
        return wrappedObject.getValue(NumberSchema.JsonFields.MULTIPLE_OF);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableNumberSchema;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
