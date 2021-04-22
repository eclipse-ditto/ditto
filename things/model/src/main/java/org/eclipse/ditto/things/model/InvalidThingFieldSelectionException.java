/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown when a {@link ThingFieldSelector} was not valid.
 *
 * @since 2.0.0
 */
@JsonParsableException(errorCode = InvalidThingFieldSelectionException.ERROR_CODE)
public final class InvalidThingFieldSelectionException extends DittoRuntimeException implements ThingException {

    private static final String DEFAULT_MESSAGE_TEMPLATE = "Thing field selection <{0}> was not valid.";
    private static final String DEFAULT_DESCRIPTION = "Please provide a comma separated List of valid Thing fields." +
            "Make sure that you did not use a space after a comma. Valid Fields are: " +
            ThingFieldSelector.SELECTABLE_FIELDS.toString();

    static final String ERROR_CODE = ERROR_CODE_PREFIX + "field.selection.invalid";

    private InvalidThingFieldSelectionException(
            final DittoHeaders dittoHeaders,
            @Nullable final String message, @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    static InvalidThingFieldSelectionException forExtraFieldSelectionString(final String extraFieldString) {
        return new Builder(MessageFormat.format(DEFAULT_MESSAGE_TEMPLATE, extraFieldString)).build();
    }

    /**
     * Constructs a new {@code InvalidThingFieldSelectionException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureDefinitionEmptyException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static InvalidThingFieldSelectionException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders,
                new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for an immutable {@code FeatureDefinitionEmptyException}.
     */
    @NotThreadSafe
    public static final class Builder extends
            DittoRuntimeExceptionBuilder<InvalidThingFieldSelectionException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String message) {
            description(DEFAULT_DESCRIPTION);
            message(message);
        }

        @Override
        protected InvalidThingFieldSelectionException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new InvalidThingFieldSelectionException(dittoHeaders, message, description, cause, href);
        }

    }
}
