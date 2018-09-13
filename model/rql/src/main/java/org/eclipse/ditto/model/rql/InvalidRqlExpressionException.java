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
package org.eclipse.ditto.model.rql;

import java.net.URI;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if an RQL expression is invalid.
 */
public class InvalidRqlExpressionException extends DittoRuntimeException implements RqlParserException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = RqlParserException.ERROR_CODE_PREFIX + "expression.invalid";

    static final String DEFAULT_DESCRIPTION = "Ensure that all opening parentheses have also closing ones and that the " +
            "RQL expression is valid.";
    static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private static final long serialVersionUID = 8900314242209005665L;

    private InvalidRqlExpressionException(final DittoHeaders dittoHeaders, final String message, final String description,
            final Throwable cause, final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code InvalidRqlExpressionException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code InvalidRqlExpressionException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new InvalidRqlExpressionException.
     */
    public static InvalidRqlExpressionException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code InvalidRqlExpressionException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new InvalidRqlExpressionException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static InvalidRqlExpressionException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link InvalidRqlExpressionException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<InvalidRqlExpressionException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected InvalidRqlExpressionException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new InvalidRqlExpressionException(dittoHeaders, message, description, cause, href);
        }
    }
}
