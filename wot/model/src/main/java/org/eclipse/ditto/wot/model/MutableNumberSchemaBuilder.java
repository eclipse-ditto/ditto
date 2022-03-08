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
 * Mutable builder for {@link NumberSchema}s.
 */
final class MutableNumberSchemaBuilder
        extends AbstractSingleDataSchemaBuilder<NumberSchema.Builder, NumberSchema>
        implements NumberSchema.Builder {

    MutableNumberSchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableNumberSchemaBuilder.class);
    }

    @Override
    DataSchemaType getDataSchemaType() {
        return DataSchemaType.NUMBER;
    }

    @Override
    public NumberSchema.Builder setMinimum(@Nullable final Double minimum) {
        putValue(NumberSchema.JsonFields.MINIMUM, minimum);
        return myself;
    }

    @Override
    public NumberSchema.Builder setExclusiveMinimum(@Nullable final Double exclusiveMinimum) {
        putValue(NumberSchema.JsonFields.EXCLUSIVE_MINIMUM, exclusiveMinimum);
        return myself;
    }

    @Override
    public NumberSchema.Builder setMaximum(@Nullable final Double maximum) {
        putValue(NumberSchema.JsonFields.MAXIMUM, maximum);
        return myself;
    }

    @Override
    public NumberSchema.Builder setExclusiveMaximum(@Nullable final Double exclusiveMaximum) {
        putValue(NumberSchema.JsonFields.EXCLUSIVE_MAXIMUM, exclusiveMaximum);
        return myself;
    }

    @Override
    public NumberSchema.Builder setMultipleOf(@Nullable final Double multipleOf) {
        putValue(NumberSchema.JsonFields.MULTIPLE_OF, multipleOf);
        return myself;
    }

    @Override
    public NumberSchema build() {
        return new ImmutableNumberSchema(wrappedObjectBuilder.build());
    }
}
