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
package org.eclipse.ditto.signals.acks.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown if an {@code AcknowledgementRequest} did not get its {@code Acknowledgement} (may it be positive or negative)
 * within a specified timeout.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementRequestTimeoutException.ERROR_CODE)
public final class AcknowledgementRequestTimeoutException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "request.timeout";

    static final String MESSAGE_TEMPLATE = "The acknowledgement request reached the specified timeout of {0}ms.";

    static final String DEFAULT_DESCRIPTION =
            "Try increasing the timeout and make sure that the requested acknowledgement is sent back in time.";

    private static final long serialVersionUID = -6234783976182314364L;

    private AcknowledgementRequestTimeoutException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE,
                HttpStatusCode.REQUEST_TIMEOUT,
                dittoHeaders,
                message,
                description,
                cause,
                href);
    }

    /**
     * Constructs a new AcknowledgementRequestTimeoutException object.
     *
     * @param timeout the timeout.
     * @throws NullPointerException if {@code timeout} is {@code null}.
     */
    public AcknowledgementRequestTimeoutException(final Duration timeout) {
        this(DittoHeaders.empty(), getMessage(timeout), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final Duration timeout) {
        checkNotNull(timeout, "timeout");
        return MessageFormat.format(MESSAGE_TEMPLATE, timeout.toMillis());
    }

    /**
     * A mutable builder for a {@code AcknowledgementRequestTimeoutException}.
     *
     * @param timeout the timeout.
     * @return the builder.
     * @throws NullPointerException if {@code timeout} is {@code null}.
     */
    public static AcknowledgementRequestTimeoutException.Builder newBuilder(final Duration timeout) {
        return new AcknowledgementRequestTimeoutException.Builder(checkNotNull(timeout, "timeout"));
    }

    /**
     * Constructs a new {@code AcknowledgementRequestTimeoutException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static AcknowledgementRequestTimeoutException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new AcknowledgementRequestTimeoutException(dittoHeaders,
                readMessage(jsonObject),
                readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION),
                null,
                readHRef(jsonObject).orElse(null));
    }

    /**
     * A mutable builder with a fluent API for a {@link AcknowledgementRequestTimeoutException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AcknowledgementRequestTimeoutException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final Duration timeout) {
            this();
            message(getMessage(timeout));
        }

        @Override
        protected AcknowledgementRequestTimeoutException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            
            return new AcknowledgementRequestTimeoutException(dittoHeaders, message, description, cause, href);
        }

    }

}
