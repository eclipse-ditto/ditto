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
package org.eclipse.ditto.json;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Thrown if JSON could not be parsed because it was in an invalid format.
 */
public final class JsonParseException extends JsonRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.invalid";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the JSON was valid (e.g. on http://jsonlint.com) and if it was in required format.";

    private static final long serialVersionUID = -7585793723086474449L;

    private JsonParseException(@Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, message, description, cause, href);
    }

    /**
     * Constructs a new {@code JsonParseException} with the specified message.
     *
     * @param message the detail message.
     */
    public JsonParseException(@Nullable final String message) {
        this(message, DEFAULT_DESCRIPTION, null, null);
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
     * A mutable builder for a {@code JsonParseException}.
     */
    @NotThreadSafe
    public static final class Builder extends AbstractJsonExceptionBuilder<JsonParseException> {

        private Builder() {
            super(ERROR_CODE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected JsonParseException doBuild(final String errorCode,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new JsonParseException(message, description, cause, href);
        }

    }

}
