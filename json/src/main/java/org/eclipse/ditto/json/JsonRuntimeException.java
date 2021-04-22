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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Common base type of all unchecked JSON exceptions.
 */
public class JsonRuntimeException extends RuntimeException implements JsonException {

    private static final long serialVersionUID = -5415257195206366631L;

    private final String errorCode;
    @Nullable private final String description;
    @Nullable private final URI href;

    /**
     * Constructs a new {@code JsonRuntimeException} object with the specified values.
     *
     * @param errorCode the error code of the exception.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode} is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     */
    protected JsonRuntimeException(final String errorCode,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(message, cause);
        checkErrorCode(errorCode);
        this.errorCode = errorCode;
        this.description = description;
        this.href = href;
    }

    /**
     * Returns a builder for fluently creating instances of {@code JsonRuntimeException}s..
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @return a new builder for JsonRuntimeException objects.
     * @throws NullPointerException if {@code errorCode} is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     */
    public static JsonExceptionBuilder<JsonRuntimeException> newBuilder(final String errorCode) {
        checkErrorCode(errorCode);
        return new Builder(errorCode);
    }

    private static void checkErrorCode(final String errorCode) {
        final String msgTemplate = "The error code of this exception must not be {0}!";
        requireNonNull(errorCode, MessageFormat.format(msgTemplate, "null"));
        if (errorCode.isEmpty()) {
            throw new IllegalArgumentException(MessageFormat.format(msgTemplate, "empty"));
        }
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public Optional<URI> getHref() {
        return Optional.ofNullable(href);
    }

    /**
     * A mutable builder for a {@code JsonRuntimeException}.
     */
    @NotThreadSafe
    public static final class Builder extends AbstractJsonExceptionBuilder<JsonRuntimeException> {

        private Builder(final String errorCode) {
            super(errorCode);
        }

        @Override
        protected JsonRuntimeException doBuild(final String errorCode,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new JsonRuntimeException(errorCode, message, description, cause, href);
        }

    }

}
