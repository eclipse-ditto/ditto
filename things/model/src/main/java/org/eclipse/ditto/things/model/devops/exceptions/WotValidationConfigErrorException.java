/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.exceptions;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown when a WoT validation config operation fails.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationConfigErrorException.ERROR_CODE)
public final class WotValidationConfigErrorException extends DittoRuntimeException implements WotValidationConfigException{

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX +  "error";

    private static final String DEFAULT_MESSAGE = "The WoT validation config operation failed.";
    private static final String DEFAULT_DESCRIPTION = "Please check the details of your request.";

    private WotValidationConfigErrorException(final DittoHeaders dittoHeaders,
                                                @Nullable final String message,
                                                @Nullable final String description,
                                                @Nullable final Throwable cause,
                                                @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigException}.
     *
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @return the builder.
     */
    public static Builder newBuilder(final String message) {
        return new Builder(message);
    }

    /**
     * A mutable builder for a {@code WotValidationConfigException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder(DEFAULT_MESSAGE);
    }

    /**
     * Constructs a new {@code WotValidationConfigException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigException.
     */
    public static WotValidationConfigErrorException fromJson(final JsonObject jsonObject,
                                                               final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link WotValidationConfigErrorException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotValidationConfigErrorException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String message) {
            this();
            message(message);
        }

        @Override
        protected WotValidationConfigErrorException doBuild(final DittoHeaders dittoHeaders,
                                                              @Nullable final String message,
                                                              @Nullable final String description,
                                                              @Nullable final Throwable cause,
                                                              @Nullable final URI href) {
            return new WotValidationConfigErrorException(dittoHeaders, message, description, cause, href);
        }
    }
} 