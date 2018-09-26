/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a placeholder in the connection configuration could not be resolved.
 */
@Immutable
public final class UnresolvedPlaceholderException extends DittoRuntimeException
        implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connection.placeholder.unresolved";

    private static final String MESSAGE_TEMPLATE = "The placeholder ''{0}'' could not be resolved.";

    private static final String DEFAULT_DESCRIPTION = "Some placeholders could not be resolved. "
            + "Check the spelling of the placeholder and make sure all required headers are set.";

    private static final long serialVersionUID = 6272495302389903822L;

    private UnresolvedPlaceholderException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static UnresolvedPlaceholderException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code UnresolvedPlaceholderException} object with the exception message extracted from
     * the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnresolvedPlaceholderException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static UnresolvedPlaceholderException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
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
