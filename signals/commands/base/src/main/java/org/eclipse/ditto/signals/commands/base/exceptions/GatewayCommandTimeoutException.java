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
package org.eclipse.ditto.signals.commands.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown if a command reached its defined timeout after no response was received.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = GatewayCommandTimeoutException.ERROR_CODE)
public final class GatewayCommandTimeoutException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "command.timeout";

    private static final String MESSAGE_TEMPLATE = "The Command reached the specified timeout of {0}ms.";

    private static final String DEFAULT_DESCRIPTION = "Try increasing the command timeout";

    private static final long serialVersionUID = -3732435554989623073L;

    private GatewayCommandTimeoutException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.REQUEST_TIMEOUT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayCommandTimeoutException}.
     *
     * @param timeout the timeout.
     * @return the builder.
     * @throws NullPointerException if {@code timeout} is {@code null}.
     */
    public static Builder newBuilder(final Duration timeout) {
        return new Builder(checkNotNull(timeout, "timeout"));
    }

    /**
     * Constructs a new {@code GatewayCommandTimeoutException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @return the new GatewayCommandTimeoutException.
     */
    public static GatewayCommandTimeoutException fromMessage(final String message) {
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
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} is missing required JSON fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contains unexpected value types.
     */
    public static GatewayCommandTimeoutException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .loadJson(jsonObject)
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayCommandTimeoutException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayCommandTimeoutException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final Duration timeout) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, timeout.toMillis()));
        }

        @Override
        protected GatewayCommandTimeoutException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new GatewayCommandTimeoutException(dittoHeaders, message, description, cause, href);
        }

    }

}
