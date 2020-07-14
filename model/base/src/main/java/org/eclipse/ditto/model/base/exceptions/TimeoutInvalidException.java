/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.exceptions;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when timeout value can not be parsed.
 * @since 1.2.0
 */
@Immutable
@JsonParsableException(errorCode = TimeoutInvalidException.ERROR_CODE)
public final class TimeoutInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "timeout.invalid";

    private static final String DEFAULT_MESSAGE = "The timeout <{0}{2}> is not inside its allowed bounds <0{2} - {1}{2}>";

    private static final String DEFAULT_DESCRIPTION = "Please choose a valid timeout.";

    private static final long serialVersionUID = -3108409113724423689L;


    /**
     * Constructs a new {@code TimeoutInvalidException} object.
     *
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    private TimeoutInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for {@code TimeoutInvalidException}.
     * @param timeout the received timeout.
     * @param maxTimeout the maximum allowed timeout.
     * @return a mutable builder.
     */
    public static TimeoutInvalidException.Builder newBuilder(final Duration timeout, final Duration maxTimeout) {
        return (Builder) new Builder()
                .message(MessageFormat.format(DEFAULT_MESSAGE, timeout.toMillis(), maxTimeout.toMillis(), "ms"))
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * A mutable builder for {@code TimeoutInvalidException}.
     * @param message the message of the exception.
     * @return a mutable builder.
     */
    public static TimeoutInvalidException.Builder newBuilder(final String message) {
        return (Builder) new Builder()
                .message(message)
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * Constructs a new {@code TimeoutInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeaderInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static TimeoutInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link TimeoutInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TimeoutInvalidException> {

        @Override
        protected TimeoutInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new TimeoutInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
