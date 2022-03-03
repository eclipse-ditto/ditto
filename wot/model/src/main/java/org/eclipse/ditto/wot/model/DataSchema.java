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

import java.util.Collection;

/**
 * A DataSchema describes a used data format which can be used for validation.
 * It may present itself as {@link SingleDataSchema} or as {@link MultipleDataSchema} containing multiple
 * {@link SingleDataSchema}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema</a>
 * @since 2.4.0
 */
public interface DataSchema {

    static BooleanSchema.Builder newBooleanSchemaBuilder() {
        return BooleanSchema.newBuilder();
    }

    static IntegerSchema.Builder newSingleIntegerSchemaBuilder() {
        return IntegerSchema.newBuilder();
    }

    static NumberSchema.Builder newSingleNumberSchemaBuilder() {
        return NumberSchema.newBuilder();
    }

    static StringSchema.Builder newSingleStringSchemaBuilder() {
        return StringSchema.newBuilder();
    }

    static ObjectSchema.Builder newSingleObjectSchemaBuilder() {
        return ObjectSchema.newBuilder();
    }

    static ArraySchema.Builder newSingleArraySchemaBuilder() {
        return ArraySchema.newBuilder();
    }

    static NullSchema.Builder newSingleNullSchemaBuilder() {
        return NullSchema.newBuilder();
    }

    static MultipleDataSchema newMultipleDataSchema(final Collection<SingleDataSchema> dataSchemas) {
        return MultipleDataSchema.of(dataSchemas);
    }
}
