/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Thrown if a {@link JsonKey} was in an invalid format.
 *
 * @since 1.2.0
 */
public final class JsonKeyInvalidException extends JsonRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.key.invalid";

    private static final String DEFAULT_DESCRIPTION = "Neither slashes nor any control characters are allowed as " +
            "part of the JSON key.";

    private static final long serialVersionUID = -3123581233292259932L;

    private JsonKeyInvalidException(@Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, message, description, cause, href);
    }

    /**
     * Returns a builder for fluently creating instances of {@code JsonKeyInvalidException}s.
     *
     * @return a new builder for JsonKeyInvalidException objects.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns a new builder containing the given message for the given JSON key.
     *
     * @param jsonKey The JSON key the message is about.
     * @param description The description to be in the exception.
     * @return a builder for {@code JsonKeyInvalidException} objects.
     */
    public static JsonExceptionBuilder<JsonKeyInvalidException> newBuilderWithDescription(
            final CharSequence jsonKey, @Nullable final String description) {
        return new Builder()
                .jsonPointer(jsonKey)
                .description(description);
    }


    /**
     * Returns a new builder already containing a default message that the JSON key is no valid.
     *
     * @param jsonKey The JSON key the message is about.
     * @return a builder for {@code JsonKeyInvalidException} objects.
     * @since 1.2.0
     */
    public static JsonExceptionBuilder<JsonKeyInvalidException> newBuilderWithoutDescription(
            final CharSequence jsonKey) {
        return new Builder()
                .jsonPointer(jsonKey)
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * A mutable builder for a {@code JsonKeyInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends AbstractJsonExceptionBuilder<JsonKeyInvalidException> {

        private Builder() {
            super(ERROR_CODE);
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Sets a message which points to the name of the invalid JSON key. Thus if this method is called, {@link
         * #message(String)} should not be called.
         *
         * @param jsonKeyString the string representation of the invalid JSON pointer.
         * @return this builder to allow method chaining.
         */
        public Builder jsonPointer(@Nullable final CharSequence jsonKeyString) {
            message(MessageFormat.format("The JSON key <{0}> is invalid!", jsonKeyString));
            return this;
        }

        @Override
        protected JsonKeyInvalidException doBuild(final String errorCode,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new JsonKeyInvalidException(message, description, cause, href);
        }

    }

}
