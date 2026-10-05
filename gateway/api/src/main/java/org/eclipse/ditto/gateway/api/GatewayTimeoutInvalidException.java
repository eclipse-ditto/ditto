/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.api;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * This exception indicates that the configured {@code "timeout"} of a HTTP call was not valid or within its allowed
 * bounds.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = GatewayTimeoutInvalidException.ERROR_CODE)
public final class GatewayTimeoutInvalidException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "timeout.invalid";

    private static final String DEFAULT_MESSAGE = "The timeout <{0}ms> is not inside its allowed bounds <0ms - {1}ms>";

    private static final String MESSAGE_WITH_UNIT =
            "The timeout <{0}{1}> is not inside its allowed bounds <0{1} - {2}{1}>";

    private static final String DEFAULT_DESCRIPTION = "Choose a timeout inside the bounds.";

    private static final long serialVersionUID = 4432789435789590723L;

    private GatewayTimeoutInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayTimeoutInvalidException}.
     *
     * @param timeout the applied timeout.
     * @param maxTimeout the configured max timeout.
     * @return the builder.
     */
    public static Builder newBuilder(final Duration timeout, final Duration maxTimeout) {
        return new Builder(timeout, maxTimeout);
    }

    /**
     * A mutable builder for a {@code GatewayTimeoutInvalidException} whose message uses the time unit the timeout
     * was specified in, e.g. {@code <90s>} instead of {@code <90000ms>}.
     *
     * @param timeout the applied timeout, as specified in the request.
     * @param maxTimeout the configured max timeout.
     * @return the builder.
     * @since 4.0.0
     */
    public static Builder newBuilder(final DittoDuration timeout, final Duration maxTimeout) {
        return new Builder(formatMessageWithUnit(timeout, maxTimeout));
    }

    /**
     * Formats both the timeout and the max timeout in the unit the timeout was specified in. Falls back to
     * milliseconds if the max timeout cannot be expressed in that unit without a remainder.
     */
    private static String formatMessageWithUnit(final DittoDuration timeout, final Duration maxTimeout) {
        final long unitMillis = timeout.getChronoUnit().getDuration().toMillis();
        final long timeoutMillis = timeout.getDuration().toMillis();
        final long maxTimeoutMillis = maxTimeout.toMillis();
        final Optional<String> suffix = getSuffix(timeout);
        if (suffix.isPresent() && unitMillis > 0 && timeoutMillis % unitMillis == 0 &&
                maxTimeoutMillis % unitMillis == 0) {
            return MessageFormat.format(MESSAGE_WITH_UNIT, String.valueOf(timeoutMillis / unitMillis), suffix.get(),
                    String.valueOf(maxTimeoutMillis / unitMillis));
        }
        return MessageFormat.format(MESSAGE_WITH_UNIT, String.valueOf(timeoutMillis), "ms",
                String.valueOf(maxTimeoutMillis));
    }

    private static Optional<String> getSuffix(final DittoDuration dittoDuration) {
        // a timeout given without unit is interpreted as seconds, render it with the explicit "s" suffix
        return Arrays.stream(DittoDuration.DittoTimeUnit.values())
                .filter(unit -> dittoDuration.getChronoUnit() == unit.getChronoUnit())
                .map(DittoDuration.DittoTimeUnit::getSuffix)
                .filter(unitSuffix -> !unitSuffix.isEmpty())
                .findFirst();
    }

    /**
     * Constructs a new {@code GatewayTimeoutInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayTimeoutInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static GatewayTimeoutInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code GatewayTimeoutInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayTimeoutInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static GatewayTimeoutInvalidException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link GatewayTimeoutInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayTimeoutInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final Duration timeout, final Duration maxTimeout) {
            message(MessageFormat.format(DEFAULT_MESSAGE, timeout.toMillis(), maxTimeout.toMillis()));
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String message) {
            message(message);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected GatewayTimeoutInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new GatewayTimeoutInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
