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

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link StringSchema}s.
 */
final class MutableStringSchemaBuilder
        extends AbstractSingleDataSchemaBuilder<StringSchema.Builder, StringSchema>
        implements StringSchema.Builder {

    MutableStringSchemaBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableStringSchemaBuilder.class);
    }

    @Override
    DataSchemaType getDataSchemaType() {
        return DataSchemaType.STRING;
    }

    @Override
    public StringSchema.Builder setMinLength(@Nullable final Integer minLength) {
        putValue(StringSchema.JsonFields.MIN_LENGTH, minLength);
        return myself;
    }

    @Override
    public StringSchema.Builder setMaxLength(@Nullable final Integer maxLength) {
        putValue(StringSchema.JsonFields.MAX_LENGTH, maxLength);
        return myself;
    }

    @Override
    public StringSchema.Builder setPattern(@Nullable final Pattern pattern) {
        if (pattern != null) {
            putValue(StringSchema.JsonFields.PATTERN, pattern.toString());
        } else {
            remove(StringSchema.JsonFields.PATTERN);
        }
        return myself;
    }

    @Override
    public StringSchema.Builder setContentEncoding(@Nullable final String contentEncoding) {
        putValue(StringSchema.JsonFields.CONTENT_ENCODING, contentEncoding);
        return myself;
    }

    @Override
    public StringSchema.Builder setMediaType(@Nullable final String mediaType) {
        putValue(StringSchema.JsonFields.CONTENT_MEDIA_TYPE, mediaType);
        return myself;
    }

    @Override
    public StringSchema build() {
        return new ImmutableStringSchema(wrappedObjectBuilder.build());
    }
}
