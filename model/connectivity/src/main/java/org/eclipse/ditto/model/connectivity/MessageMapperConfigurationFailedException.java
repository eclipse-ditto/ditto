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
 * Thrown if the the configuration of a Message Mapper failed.
 */
@Immutable
public final class MessageMapperConfigurationFailedException extends DittoRuntimeException implements
        ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.mapper.config.failed";

    private static final String MESSAGE_TEMPLATE = "The message mapper configuration failed due to: {0}";

    private static final String DEFAULT_DESCRIPTION = "Check the configuration options of your mapper for errors.";

    private static final long serialVersionUID = 3108439347153942427L;

    private MessageMapperConfigurationFailedException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageMapperConfigurationFailedException}.
     *
     * @param errorMessage the errorMessage describing why the mapper configuration failed.
     * @return the builder.
     */
    public static Builder newBuilder(final String errorMessage) {
        return new Builder(errorMessage);
    }

    /**
     * Constructs a new {@code MessageMapperConfigurationFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMapperConfigurationFailedException.
     */
    public static MessageMapperConfigurationFailedException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code MessageMapperConfigurationFailedException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new MessageMapperConfigurationFailedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static MessageMapperConfigurationFailedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link MessageMapperConfigurationFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageMapperConfigurationFailedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String errorMessage) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, errorMessage));
        }

        @Override
        protected MessageMapperConfigurationFailedException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new MessageMapperConfigurationFailedException(dittoHeaders, message, description, cause, href);
        }
    }

}
