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
package org.eclipse.ditto.model.connectivity;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if the mapping of an arbitrary {@link ExternalMessage} failed.
 */
@Immutable
public final class MessageMappingFailedException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.mapping.failed";

    private static final String MESSAGE_TEMPLATE = "The external message with content-type ''{0}'' could not be mapped.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if you are sending the correct content-type/payload combination an that you have registered a mapper " +
                    "which transforms your payload to Ditto Protocol";

    private static final String CONTENT_TYPE_MISSING_DESCRIPTION =
            "Make sure you specify the 'Content-Type' when sending your message";

    private static final long serialVersionUID = -6312489434534126579L;

    private MessageMappingFailedException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static MessageMappingFailedException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code MessageMappingFailedException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMappingFailedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static MessageMappingFailedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
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
        protected MessageMappingFailedException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new MessageMappingFailedException(dittoHeaders, message, description, cause, href);
        }
    }

}
