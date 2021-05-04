/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown if sending of an {@code Signal} to an external system failed.
 */
@Immutable
@JsonParsableException(errorCode = MessageSendingFailedException.ERROR_CODE)
public final class MessageSendingFailedException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.sending.failed";

    private static final HttpStatus DEFAULT_HTTP_STATUS = HttpStatus.SERVICE_UNAVAILABLE;
    private static final String MESSAGE_TEMPLATE = "Failed to send message: {0}";
    private static final String DEFAULT_MESSAGE = "Failed to send message.";
    private static final String DEFAULT_DESCRIPTION = "Sending the message to an external system failed, " +
            "please check if your connection is configured properly and the target system is available and consuming " +
            "messages.";

    private static final long serialVersionUID = 8762467293113632771L;

    private MessageSendingFailedException(final DittoHeaders dittoHeaders,
            final HttpStatus httpStatus,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, httpStatus, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageSendingFailedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code MessageSendingFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageSendingFailedException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static MessageSendingFailedException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code MessageSendingFailedException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageSendingFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static MessageSendingFailedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
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
     * A mutable builder with a fluent API for a {@link MessageSendingFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageSendingFailedException> {

        private HttpStatus httpStatus = DEFAULT_HTTP_STATUS;

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Set the HTTP status of this builder.
         *
         * @param httpStatus the new HTTP status.
         * @return this builder.
         * @since 2.0.0
         */
        public Builder httpStatus(final HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        @Override
        public Builder cause(@Nullable final Throwable cause) {
            if (cause == null) {
                message(DEFAULT_MESSAGE);
            } else {
                super.cause(cause);
                message(MessageFormat.format(MESSAGE_TEMPLATE, cause.getMessage()));
            }
            return this;
        }

        @Override
        protected MessageSendingFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new MessageSendingFailedException(dittoHeaders, httpStatus, message, description, cause, href);
        }

    }

}
