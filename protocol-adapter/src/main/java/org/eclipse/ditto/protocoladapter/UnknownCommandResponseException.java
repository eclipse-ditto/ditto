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
package org.eclipse.ditto.protocoladapter;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a {@link org.eclipse.ditto.signals.commands.base.CommandResponse} is not supported.
 */
public final class UnknownCommandResponseException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "things.protocol.adapter:unknown.response";

    private static final String MESSAGE_TEMPLATE = "The response ''{0}'' is not supported by the adapter.";

    private static final String DEFAULT_DESCRIPTION = "Check if the response is correct.";

    private static final long serialVersionUID = 3886410099324151406L;

    private UnknownCommandResponseException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code UnknownCommandResponseException}.
     *
     * @param responseName the response not supported.
     * @return the builder.
     */
    public static Builder newBuilder(final String responseName) {
        return new Builder(responseName);
    }

    /**
     * Constructs a new {@code UnknownCommandResponseException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnknownCommandResponseException.
     */
    public static UnknownCommandResponseException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder() //
                .dittoHeaders(dittoHeaders) //
                .message(message) //
                .build();
    }

    /**
     * Constructs a new {@code UnknownCommandResponseException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnknownCommandResponseException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static UnknownCommandResponseException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link UnknownCommandResponseException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<UnknownCommandResponseException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String responseName) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, responseName));
        }

        @Override
        protected UnknownCommandResponseException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new UnknownCommandResponseException(dittoHeaders, message, description, cause, href);
        }
    }

}
