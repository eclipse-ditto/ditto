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
 * Thrown if the the configuration of a Message Mapper was invalid.
 */
@Immutable
public final class MessageMapperConfigurationInvalidException extends DittoRuntimeException implements
        ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.mapper.config.invalid";

    private static final String MESSAGE_TEMPLATE = "The message mapper was not configured correctly as property ''{0}'' was missing from the mapper's configuration.";

    private static final String DEFAULT_DESCRIPTION = "Make sure to add the missing property to the mapper's configuration.";

    private static final long serialVersionUID = -2538489434734124572L;

    private MessageMapperConfigurationInvalidException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageMapperConfigurationInvalidException}.
     *
     * @param missingConfigurationProperty the missingConfigurationProperty which was not present in the config.
     * @return the builder.
     */
    public static Builder newBuilder(final String missingConfigurationProperty) {
        return new Builder(missingConfigurationProperty);
    }

    /**
     * Constructs a new {@code MessageMapperConfigurationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMapperConfigurationInvalidException.
     */
    public static MessageMapperConfigurationInvalidException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code MessageMapperConfigurationInvalidException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMapperConfigurationInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static MessageMapperConfigurationInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link MessageMapperConfigurationInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageMapperConfigurationInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String missingConfigurationProperty) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, missingConfigurationProperty));
        }

        @Override
        protected MessageMapperConfigurationInvalidException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new MessageMapperConfigurationInvalidException(dittoHeaders, message, description, cause, href);
        }
    }

}
