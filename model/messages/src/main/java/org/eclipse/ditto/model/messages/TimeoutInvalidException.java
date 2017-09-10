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
package org.eclipse.ditto.model.messages;

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
 * Thrown if the timeout of a message was invalid (too low or too high).
 */
@Immutable
public final class TimeoutInvalidException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "timeout.invalid";

    private static final String MESSAGE_TEMPLATE = "Timeout {0} seconds is not inside its allowed bounds 0 - {1} "
            + "seconds!";

    private static final String DEFAULT_DESCRIPTION = "Choose a timeout inside the bounds.";

    private static final long serialVersionUID = 217695797034231310L;

    /**
     * Constructs a new {@code TimeoutInvalidException} object.
     *
     * @param timeout the invalid timeout in seconds.
     * @param maxTimeout the maximum allowed timeout in seconds.
     */
    public TimeoutInvalidException(final Long timeout, final Long maxTimeout) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, timeout, maxTimeout), DEFAULT_DESCRIPTION,
                null, null);
    }

    private TimeoutInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code TimeoutInvalidException}.
     *
     * @param timeout invalid timeout in seconds.
     * @param maxTimeout the maximum allowed timeout in seconds.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final Long timeout, @Nullable final Long maxTimeout) {
        return new Builder(timeout, maxTimeout);
    }

    /**
     * Constructs a new {@code TimeoutInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @return the new TimeoutInvalidException.
     */
    public static TimeoutInvalidException fromMessage(final String message) {
        return new Builder()
                .message(message)
                .build();
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TimeoutInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Builder builder = new Builder();
        builder.loadJson(jsonObject);
        builder.dittoHeaders(dittoHeaders);
        return builder.build();
    }

    /**
     * A mutable builder with a fluent API for a {@link TimeoutInvalidException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TimeoutInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final Long timeout, @Nullable final Long maxTimeout) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, timeout, maxTimeout));
        }

        @Override
        protected TimeoutInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new TimeoutInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
