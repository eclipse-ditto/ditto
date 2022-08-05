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
package org.eclipse.ditto.base.model.signals;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.exceptions.GeneralException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * Thrown if a {@link Signal} is not supported.
 */
@Immutable
@JsonParsableException(errorCode = UnsupportedSignalException.ERROR_CODE)
public final class UnsupportedSignalException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "signal.unsupported";

    static final String MESSAGE_TEMPLATE = "The provided signal ''{0}'' is not supported.";

    static final String DEFAULT_DESCRIPTION = "Check if you specified the correct resource/path and payload.";

    private static final long serialVersionUID = -8102351974097361762L;

    private UnsupportedSignalException(final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, httpStatus, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code UnsupportedSignalException}.
     *
     * @param signal the signal that is not supported.
     * @return the builder.
     * @throws NullPointerException if {@code signal} is {@code null}.
     */
    public static Builder newBuilder(final String signal) {
        return new Builder(checkNotNull(signal, "signal"));
    }

    /**
     * Constructs a new {@code UnsupportedSignalException} object with given message.
     * The HTTP status of the returned exception is {@link HttpStatus#BAD_REQUEST}.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnsupportedSignalException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static UnsupportedSignalException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code UnsupportedSignalException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnsupportedSignalException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static UnsupportedSignalException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        checkNotNull(jsonObject, "jsonObject");

        final Builder builder = new Builder();
        builder.dittoHeaders(checkNotNull(dittoHeaders, "dittoHeaders"));

        try {
            builder.message(jsonObject.getValueOrThrow(JsonFields.MESSAGE));
            jsonObject.getValue(JsonFields.STATUS).flatMap(HttpStatus::tryGetInstance).ifPresent(builder::httpStatus);
            jsonObject.getValue(JsonFields.DESCRIPTION).ifPresent(builder::description);
            jsonObject.getValue(JsonFields.HREF).map(URI::create).ifPresent(builder::href);
        } catch (final Exception e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize JSON object to a {0}: {1}",
                            UnsupportedSignalException.class.getSimpleName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }

        return builder.build();
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .httpStatus(getHttpStatus())
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link UnsupportedSignalException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<UnsupportedSignalException> {

        private HttpStatus httpStatus;

        private Builder() {
            httpStatus = HttpStatus.BAD_REQUEST;
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String signal) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, signal));
        }

        /**
         * Sets the specified {@code HttpStatus} of the exception to be built.
         * If not set, the default HTTP status of the exception is {@link HttpStatus#BAD_REQUEST}.
         *
         * @param httpStatus the HTTP status of the built exception.
         * @return this builder instance to allow method chaining.
         * @throws IllegalArgumentException if {@code httpStatus} is neither a client error (4xx)
         * nor a server error (5xx).
         */
        public Builder httpStatus(final HttpStatus httpStatus) {
            this.httpStatus = checkNotNull(httpStatus, "httpStatus");
            if (!httpStatus.isClientError() && !httpStatus.isServerError()) {
                final String pattern = "Category of <{0}> is neither client error nor server error.";
                throw new IllegalArgumentException(MessageFormat.format(pattern, httpStatus));
            }
            return this;
        }

        @Override
        protected UnsupportedSignalException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new UnsupportedSignalException(httpStatus, dittoHeaders, message, description, cause, href);
        }

    }

}
