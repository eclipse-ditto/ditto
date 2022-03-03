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
 * Mutable builder for {@link ArraySchema}s.
 */
final class MutableArraySchemaBuilder
        extends AbstractSingleDataSchemaBuilder<ArraySchema.Builder, ArraySchema>
        implements ArraySchema.Builder {

    MutableArraySchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableArraySchemaBuilder.class);
    }

    @Override
    DataSchemaType getDataSchemaType() {
        return DataSchemaType.ARRAY;
    }

    @Override
    public ArraySchema.Builder setItems(@Nullable final DataSchema items) {
        if (items != null) {
            if (items instanceof MultipleDataSchema) {
                putValue(ArraySchema.JsonFields.ITEMS_MULTIPLE, ((MultipleDataSchema) items).toJson());
            } else if (items instanceof SingleDataSchema) {
                putValue(ArraySchema.JsonFields.ITEMS, ((SingleDataSchema) items).toJson());
            } else {
                throw new IllegalArgumentException("Unsupported items: " + items.getClass().getSimpleName());
            }
        } else {
            remove(ArraySchema.JsonFields.ITEMS);
        }
        return myself;
    }

    @Override
    public ArraySchema.Builder setMinItems(@Nullable final Integer minItems) {
        putValue(ArraySchema.JsonFields.MIN_ITEMS, minItems);
        return myself;
    }

    @Override
    public ArraySchema.Builder setMaxItems(@Nullable final Integer maxItems) {
        putValue(ArraySchema.JsonFields.MAX_ITEMS, maxItems);
        return myself;
    }

    @Override
    public ArraySchema build() {
        return new ImmutableArraySchema(wrappedObjectBuilder.build());
    }
}
