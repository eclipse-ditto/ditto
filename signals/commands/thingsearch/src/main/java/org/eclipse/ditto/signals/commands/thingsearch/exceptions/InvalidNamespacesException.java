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
package org.eclipse.ditto.signals.commands.thingsearch.exceptions;

import java.net.URI;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a filter string for a search is invalid.
 */
public class InvalidNamespacesException extends DittoRuntimeException implements ThingSearchException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "search.namespaces.invalid";

    static final String DEFAULT_DESCRIPTION = "The list of provided namespaces is too long.";
    static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private static final long serialVersionUID = 8900314242209005665L;

    private InvalidNamespacesException(final DittoHeaders dittoHeaders, final String message, final String description,
            final Throwable cause, final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code InvalidFilterException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code InvalidFilterException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new InvalidFilterException.
     */
    public static InvalidNamespacesException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code InvalidFilterException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new InvalidFilterException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static InvalidNamespacesException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link InvalidNamespacesException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<InvalidNamespacesException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected InvalidNamespacesException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new InvalidNamespacesException(dittoHeaders, message, description, cause, href);
        }
    }
}
