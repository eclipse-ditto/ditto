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
package org.eclipse.ditto.signals.base;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a JSON string or object could not be parsed as it was unexpected/unknown.
 */
@Immutable
public final class JsonTypeNotParsableException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.type.notparsable";

    private static final String MESSAGE_TEMPLATE = "The JSON type ''{0}'' is not supported by registry ''{1}''.";

    private static final String DEFAULT_DESCRIPTION = "Check if you sent the JSON with the correct type.";

    private static final long serialVersionUID = 1764112162089631698L;

    private JsonTypeNotParsableException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code JsonTypeNotParsableException}.
     *
     * @param type the not supported type.
     * @param registryName the name of the registry which did not support the type.
     * @return the builder.
     */
    public static Builder newBuilder(final String type, final String registryName) {
        return new Builder(type, registryName);
    }

    /**
     * Constructs a new {@code JsonTypeNotParsableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new JsonTypeNotParsableException.
     */
    public static JsonTypeNotParsableException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code JsonTypeNotParsableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new JsonTypeNotParsableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static JsonTypeNotParsableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link JsonTypeNotParsableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<JsonTypeNotParsableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String type, final String registryName) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, type, registryName));
        }

        @Override
        protected JsonTypeNotParsableException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new JsonTypeNotParsableException(dittoHeaders, message, description, cause, href);
        }
    }
}
