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
 * Thrown if the mapping of an arbitrary {@code ExternalMessage} failed.
 */
@Immutable
@JsonParsableException(errorCode = MessageMappingFailedException.ERROR_CODE)
public final class MessageMappingFailedException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.mapping.failed";

    private static final String MESSAGE_TEMPLATE =
            "The external message with content-type ''{0}'' could not be mapped.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if you are sending the correct content-type/payload combination an that you have registered a mapper " +
                    "which transforms your payload to Ditto Protocol";

    private static final String CONTENT_TYPE_MISSING_DESCRIPTION =
            "Make sure you specify the 'Content-Type' when sending your message";

    private static final long serialVersionUID = -6312489434534126579L;

    private MessageMappingFailedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageMappingFailedException}.
     *
     * @param contentType the contentType of the ExternalMessage which could not be mapped or {@code null} if there was
     * not a contentType present.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final String contentType) {
        return new Builder(contentType);
    }

    /**
     * Constructs a new {@code MessageMappingFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMappingFailedException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static MessageMappingFailedException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code MessageMappingFailedException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMappingFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static MessageMappingFailedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link MessageMappingFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageMappingFailedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final String contentType) {
            this();
            final boolean contentTypeAvailable = contentType != null && !contentType.isEmpty();
            message(MessageFormat.format(MESSAGE_TEMPLATE, contentTypeAvailable ? contentType : "<unspecified>"));
            if (!contentTypeAvailable) {
                description(CONTENT_TYPE_MISSING_DESCRIPTION);
            }
        }

        @Override
        protected MessageMappingFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new MessageMappingFailedException(dittoHeaders, message, description, cause, href);
        }
    }

}
