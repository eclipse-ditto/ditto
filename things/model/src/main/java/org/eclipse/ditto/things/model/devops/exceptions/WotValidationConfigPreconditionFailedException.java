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
import java.text.MessageFormat;

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
 * Thrown when validating a precondition header fails on a WoT validation config.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationConfigPreconditionFailedException.ERROR_CODE)
public final class WotValidationConfigPreconditionFailedException extends DittoRuntimeException
        implements WotValidationConfigException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "precondition.failed";

    private static final String MESSAGE_TEMPLATE =
            "The comparison of precondition header ''{0}'' for the requested WoT validation config resource evaluated to false." +
                    " Header value: ''{1}'', actual entity-tag: ''{2}''.";

    private static final String DEFAULT_DESCRIPTION = "The comparison of the provided precondition header with the " +
            "current ETag value of the requested WoT validation config resource evaluated to false. Check the value of your " +
            "conditional header value.";

    private WotValidationConfigPreconditionFailedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.PRECONDITION_FAILED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link WotValidationConfigPreconditionFailedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@link WotValidationConfigPreconditionFailedException}.
     *
     * @param conditionalHeaderName the name of the conditional header.
     * @param expected the expected value.
     * @param actual the actual ETag value.
     * @return the builder.
     */
    public static Builder newBuilder(final String conditionalHeaderName, final String expected, final String actual) {
        return new Builder(conditionalHeaderName, expected, actual);
    }

    /**
     * Constructs a new {@link WotValidationConfigPreconditionFailedException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigPreconditionFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotValidationConfigPreconditionFailedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link WotValidationConfigPreconditionFailedException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<WotValidationConfigPreconditionFailedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String conditionalHeaderName, final String expected, final String actual) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, conditionalHeaderName, expected, actual));
        }

        @Override
        protected WotValidationConfigPreconditionFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotValidationConfigPreconditionFailedException(dittoHeaders, message, description, cause, href);
        }

    }
} 