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
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link StringSchema}.
 */
@Immutable
final class ImmutableStringSchema extends AbstractSingleDataSchema implements StringSchema {

    ImmutableStringSchema(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.STRING);
    }

    @Override
    public Optional<Integer> getMinLength() {
        return wrappedObject.getValue(StringSchema.JsonFields.MIN_LENGTH);
    }

    @Override
    public Optional<Integer> getMaxLength() {
        return wrappedObject.getValue(StringSchema.JsonFields.MAX_LENGTH);
    }

    @Override
    public Optional<Pattern> getPattern() {
        return wrappedObject.getValue(StringSchema.JsonFields.PATTERN)
                .map(Pattern::compile);
    }

    @Override
    public Optional<String> getContentEncoding() {
        return wrappedObject.getValue(StringSchema.JsonFields.CONTENT_ENCODING);
    }

    @Override
    public Optional<String> getContentMediaType() {
        return wrappedObject.getValue(StringSchema.JsonFields.CONTENT_MEDIA_TYPE);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableStringSchema;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
