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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link IntegerSchema}s.
 */
final class MutableIntegerSchemaBuilder
        extends AbstractSingleDataSchemaBuilder<IntegerSchema.Builder, IntegerSchema>
        implements IntegerSchema.Builder {

    MutableIntegerSchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableIntegerSchemaBuilder.class);
    }

    @Override
    DataSchemaType getDataSchemaType() {
        return DataSchemaType.INTEGER;
    }

    @Override
    public IntegerSchema.Builder setMinimum(@Nullable final Integer minimum) {
        putValue(IntegerSchema.JsonFields.MINIMUM, minimum);
        return myself;
    }

    @Override
    public IntegerSchema.Builder setExclusiveMinimum(@Nullable final Integer exclusiveMinimum) {
        putValue(IntegerSchema.JsonFields.EXCLUSIVE_MINIMUM, exclusiveMinimum);
        return myself;
    }

    @Override
    public IntegerSchema.Builder setMaximum(@Nullable final Integer maximum) {
        putValue(IntegerSchema.JsonFields.MAXIMUM, maximum);
        return myself;
    }

    @Override
    public IntegerSchema.Builder setExclusiveMaximum(@Nullable final Integer exclusiveMaximum) {
        putValue(IntegerSchema.JsonFields.EXCLUSIVE_MAXIMUM, exclusiveMaximum);
        return myself;
    }

    @Override
    public IntegerSchema.Builder setMultipleOf(@Nullable final Integer multipleOf) {
        putValue(IntegerSchema.JsonFields.MULTIPLE_OF, multipleOf);
        return myself;
    }

    @Override
    public IntegerSchema build() {
        return new ImmutableIntegerSchema(wrappedObjectBuilder.build());
    }
}
