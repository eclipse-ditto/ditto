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
 * Thrown when validating a precondition header on a WoT validation config leads to status
 * {@link org.eclipse.ditto.base.model.common.HttpStatus#NOT_MODIFIED}.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationConfigPreconditionNotModifiedException.ERROR_CODE)
public final class WotValidationConfigPreconditionNotModifiedException extends DittoRuntimeException  implements WotValidationConfigException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "precondition.notmodified";

    private static final String MESSAGE_TEMPLATE =
            "The comparison of precondition header ''if-none-match'' for the requested WoT validation config resource evaluated to " +
                    "false. Expected: ''{0}'' not to match actual: ''{1}''.";

    private static final String DEFAULT_DESCRIPTION =
            "The comparison of the provided precondition header ''if-none-match'' with the current ETag value of the " +
                    "requested WoT validation config resource evaluated to false. Check the value of your conditional header value.";

    private WotValidationConfigPreconditionNotModifiedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_MODIFIED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link WotValidationConfigPreconditionNotModifiedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@link WotValidationConfigPreconditionNotModifiedException}.
     *
     * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
     * @param matched the matched value.
     * @return the builder.
     */
    public static Builder newBuilder(final String expectedNotToMatch, final String matched) {
        return new Builder(expectedNotToMatch, matched);
    }

    /**
     * Constructs a new {@link WotValidationConfigPreconditionNotModifiedException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationConfigPreconditionNotModifiedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotValidationConfigPreconditionNotModifiedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link WotValidationConfigPreconditionNotModifiedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotValidationConfigPreconditionNotModifiedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String expectedNotToMatch, final String matched) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, expectedNotToMatch, matched));
        }

        @Override
        protected WotValidationConfigPreconditionNotModifiedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotValidationConfigPreconditionNotModifiedException(dittoHeaders, message, description, cause, href);
        }
    }
} 