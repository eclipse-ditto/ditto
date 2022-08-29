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

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Thrown if a field selector at was in an invalid format.
 */
public final class JsonFieldSelectorInvalidException extends JsonRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.fieldselector.invalid";

    private static final String DEFAULT_DESCRIPTION =
            "Check, for example, if the amount of opening parentheses '(' matches the amount of closing ones ')'.";

    private static final long serialVersionUID = -8483368816839385508L;

    private JsonFieldSelectorInvalidException(@Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, message, description, cause, href);
    }

    /**
     * Returns a builder for fluently creating instances of {@code JsonParseException}s..
     *
     * @return a new builder for JsonParseException objects.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code JsonPointerInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends AbstractJsonExceptionBuilder<JsonFieldSelectorInvalidException> {

        private Builder() {
            super(ERROR_CODE);
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Sets a message which points to the invalid field selector. Thus if this method is called, {@link #message}
         * should not be called.
         *
         * @param fieldSelector the invalid field selector string.
         * @return this builder to allow method chaining.
         */
        public Builder fieldSelector(final String fieldSelector) {
            message(MessageFormat.format("The field selector <{0}> is invalid!", fieldSelector));
            return this;
        }

        @Override
        protected JsonFieldSelectorInvalidException doBuild(final String errorCode,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new JsonFieldSelectorInvalidException(message, description, cause, href);
        }

    }

}
