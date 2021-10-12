/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

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
 * Thrown if a placeholder could not be resolved.
 */
@Immutable
@JsonParsableException(errorCode = UnresolvedPlaceholderException.ERROR_CODE)
public final class UnresolvedPlaceholderException extends DittoRuntimeException
        implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "unresolved";

    private static final String MESSAGE_TEMPLATE = "The placeholder ''{0}'' could not be resolved.";

    private static final String DEFAULT_DESCRIPTION = "Some placeholders could not be resolved. "
            + "Check the spelling of the placeholder and make sure all required headers are set.";

    private static final long serialVersionUID = 6272495302389903822L;

    private UnresolvedPlaceholderException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code {@link UnresolvedPlaceholderException}}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code {@link UnresolvedPlaceholderException}}.
     *
     * @param unresolvedPlaceholder the unresolved placeholder.
     * @return the builder.
     */
    public static Builder newBuilder(final String unresolvedPlaceholder) {
        return new Builder(unresolvedPlaceholder);
    }

    /**
     * Constructs a new {@code UnresolvedPlaceholderException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnresolvedPlaceholderException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static UnresolvedPlaceholderException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code UnresolvedPlaceholderException} object with the exception message extracted from
     * the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnresolvedPlaceholderException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static UnresolvedPlaceholderException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link UnresolvedPlaceholderException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<UnresolvedPlaceholderException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String unresolvedPlaceholder) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, unresolvedPlaceholder));
        }

        @Override
        protected UnresolvedPlaceholderException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new UnresolvedPlaceholderException(dittoHeaders, message, description, cause, href);
        }

    }

}
