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
package org.eclipse.ditto.model.base.exceptions;

import static java.text.MessageFormat.format;
import static java.util.Objects.requireNonNull;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown when an (external) header value can not be converted to a Ditto header.
 */
@Immutable
public final class DittoHeaderInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "header.invalid";

    private static final String DEFAULT_MESSAGE = "The value of a header is invalid.";

    private static final String MESSAGE_TEMPLATE = "The value ''{0}'' of the header ''{1}'' is not a valid {2}.";

    private static final String DEFAULT_DESCRIPTION = "Verify that the header has the correct syntax and try again.";

    private static final String DESCRIPTION_TEMPLATE =
            "Verify that the value of the header ''{0}'' is a valid ''{1}'' and try again.";


    /**
     * Constructs a new {@code DittoRuntimeException} object.
     *
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    private DittoHeaderInvalidException(
            final DittoHeaders dittoHeaders,
            @Nullable final String message, @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerName the key of the header.
     * @param headerValue the value of the header.
     * @param headerType the type of the header. (int, String, entity-tag...)
     * @return the builder.
     */
    public static DittoHeaderInvalidException.Builder newInvalidTypeBuilder(final String headerName,
            final String headerValue, final String headerType) {

        return new DittoHeaderInvalidException.Builder(headerName, headerValue, headerType);
    }

    /**
     *  A mutable builder for a {@link DittoHeaderInvalidException} with a custom message.
     *
     * @param customMessage the custom message
     * @return the builder.
     */
    public static DittoHeaderInvalidException.Builder newCustomMessageBuilder(final String customMessage) {
        return new DittoHeaderInvalidException.Builder(customMessage);
    }

    /**
     * Constructs a new {@link DittoHeaderInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link DittoHeaderInvalidException}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static DittoHeaderInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    private static DittoHeaderInvalidException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }
    /**
     * A mutable builder with a fluent API for a {@link DittoHeaderInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoHeaderInvalidException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String headerName, final String headerValue, final String headerType) {
            message(format(MESSAGE_TEMPLATE, requireNonNull(headerValue), requireNonNull(headerName),
                    requireNonNull(headerType)));
            description(format(DESCRIPTION_TEMPLATE, requireNonNull(headerName), requireNonNull(headerType)));
        }

        private Builder(final String customMessage) {
            this();
            message(customMessage);
        }

        protected DittoHeaderInvalidException doBuild(DittoHeaders dittoHeaders, @Nullable String message,
                @Nullable String description, @Nullable Throwable cause, @Nullable URI href) {
            return new DittoHeaderInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
