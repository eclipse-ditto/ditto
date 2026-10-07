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
package org.eclipse.ditto.base.model.exceptions;

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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown when timeout value can not be parsed.
 *
 * @since 1.2.0
 */
@Immutable
@JsonParsableException(errorCode = TimeoutInvalidException.ERROR_CODE)
public final class TimeoutInvalidException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "timeout.invalid";

    private static final String DEFAULT_MESSAGE =
            "The timeout <{0}{2}> is not inside its allowed bounds <0{2} - {1}{2}>";

    private static final String DEFAULT_DESCRIPTION = "Please choose a valid timeout.";

    private static final long serialVersionUID = -3108409113724423689L;

    private TimeoutInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for {@code TimeoutInvalidException}.
     *
     * @param timeout the received timeout.
     * @param maxTimeout the maximum allowed timeout.
     * @return a mutable builder.
     */
    public static Builder newBuilder(final Duration timeout, final Duration maxTimeout) {
        return (Builder) new Builder()
                .message(MessageFormat.format(DEFAULT_MESSAGE, timeout.toMillis(), maxTimeout.toMillis(), "ms"))
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * A mutable builder for {@code TimeoutInvalidException} whose message uses the time unit the timeout was
     * specified in, e.g. {@code <90s>} instead of {@code <90000ms>}.
     *
     * @param timeout the received timeout, as specified in the request.
     * @param maxTimeout the maximum allowed timeout.
     * @return a mutable builder.
     * @since 4.0.0
     */
    public static Builder newBuilder(final DittoDuration timeout, final Duration maxTimeout) {
        return (Builder) new Builder()
                .message(formatMessageWithUnit(timeout, maxTimeout))
                .description(DEFAULT_DESCRIPTION);
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
            return MessageFormat.format(DEFAULT_MESSAGE, String.valueOf(timeoutMillis / unitMillis),
                    String.valueOf(maxTimeoutMillis / unitMillis), suffix.get());
        }
        return MessageFormat.format(DEFAULT_MESSAGE, String.valueOf(timeoutMillis), String.valueOf(maxTimeoutMillis),
                "ms");
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
     * A mutable builder for {@code TimeoutInvalidException}.
     *
     * @param message the message of the exception.
     * @return a mutable builder.
     */
    public static Builder newBuilder(final String message) {
        return (Builder) new Builder()
                .message(message)
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * Constructs a new {@code TimeoutInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeaderInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TimeoutInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.exceptions.TimeoutInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TimeoutInvalidException> {

        public Builder() {
            description(DEFAULT_DESCRIPTION);
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
