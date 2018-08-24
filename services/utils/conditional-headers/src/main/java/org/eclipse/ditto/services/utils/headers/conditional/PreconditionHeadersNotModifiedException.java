/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

public class PreconditionHeadersNotModifiedException extends DittoRuntimeException {


    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "headers.precondition.notmodified";

    private static final String MESSAGE_TEMPLATE =
            "The comparison of precondition header ''If-None-Match'' evaluated to false. " +
                    "Expected: ''{1}'' not to match actual: ''{2}''.";

    private static final String DEFAULT_DESCRIPTION = "The comparison of the provided precondition header with the " +
            "current ETag value evaluated to false. Check the value of your conditional header value.";

    private PreconditionHeadersNotModifiedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_MODIFIED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code {@link PreconditionHeadersNotModifiedException }}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code {@link PreconditionHeadersNotModifiedException }}.
     *
     * @param expected the expected value.
     * @param actual the actual ETag value.
     * @return the builder.
     */
    public static Builder newBuilder(final String expected, final String actual) {
        return new Builder(expected, actual);
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersNotModifiedException.
     */
    public static PreconditionHeadersNotModifiedException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersNotModifiedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static PreconditionHeadersNotModifiedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link PreconditionHeadersNotModifiedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PreconditionHeadersNotModifiedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String expected, final String actual) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, expected, actual));
        }

        @Override
        protected PreconditionHeadersNotModifiedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PreconditionHeadersNotModifiedException(dittoHeaders, message, description, cause, href);
        }

    }


}
