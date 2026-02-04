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
 * <p>
 * It may present itself as {@link SingleDataSchema} (a single type definition) or as {@link MultipleDataSchema}
 * containing multiple {@link SingleDataSchema}s for union types.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dataschema">WoT TD DataSchema</a>
 * @since 2.4.0
 */
public interface DataSchema {

    /**
     * Creates a new builder for building a {@link BooleanSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#booleanschema">WoT TD BooleanSchema</a>
     */
    static BooleanSchema.Builder newBooleanSchemaBuilder() {
        return BooleanSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link IntegerSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema</a>
     */
    static IntegerSchema.Builder newSingleIntegerSchemaBuilder() {
        return IntegerSchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link NumberSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema</a>
     */
    static NumberSchema.Builder newSingleNumberSchemaBuilder() {
        return NumberSchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link StringSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema</a>
     */
    static StringSchema.Builder newSingleStringSchemaBuilder() {
        return StringSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link ObjectSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#objectschema">WoT TD ObjectSchema</a>
     */
    static ObjectSchema.Builder newSingleObjectSchemaBuilder() {
        return ObjectSchema.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link ArraySchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema</a>
     */
    static ArraySchema.Builder newSingleArraySchemaBuilder() {
        return ArraySchema.newBuilder();
    }

    /**
     * Creates a new builder for building a {@link NullSchema}.
     *
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nullschema">WoT TD NullSchema</a>
     */
    static NullSchema.Builder newSingleNullSchemaBuilder() {
        return NullSchema.newBuilder();
    }

    /**
     * Creates a new {@link MultipleDataSchema} containing the given collection of schemas.
     *
     * @param dataSchemas the collection of data schemas to include.
     * @return the MultipleDataSchema.
     */
    static MultipleDataSchema newMultipleDataSchema(final Collection<SingleDataSchema> dataSchemas) {
        return MultipleDataSchema.of(dataSchemas);
    }
}
