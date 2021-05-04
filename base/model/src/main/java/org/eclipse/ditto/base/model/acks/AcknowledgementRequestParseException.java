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
package org.eclipse.ditto.base.model.acks;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * This exception is thrown if parsing an AcknowledgementRequest from a string representation failed.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementRequestParseException.ERROR_CODE)
public final class AcknowledgementRequestParseException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "request.parsing.failed";

    static final String MESSAGE_TEMPLATE = "<{0}> cannot be parsed to an acknowledgement request!";

    static final String DEFAULT_DESCRIPTION =
            "The string to be parsed must be a valid representation of an acknowledgement request.";

    private static final long serialVersionUID = 6452961856865574058L;

    private AcknowledgementRequestParseException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE,
                HttpStatus.UNPROCESSABLE_ENTITY,
                dittoHeaders,
                message,
                description,
                cause,
                href);
    }

    /**
     * Constructs a new {@code AcknowledgementRequestParseException} object.
     *
     * @param ackRequestString the supposed string representation of an AcknowledgementRequest that caused the
     * exception.
     * @param cause the optional cause of the exception.
     * @param dittoHeaders the headers of the exception.
     */
    public AcknowledgementRequestParseException(final CharSequence ackRequestString, @Nullable final Throwable cause,
            final DittoHeaders dittoHeaders) {

        this(dittoHeaders, getMessageIncludingCause(ackRequestString, cause), DEFAULT_DESCRIPTION, cause, null);
    }

    private static String getMessageIncludingCause(final CharSequence ackRequestString,
            @Nullable final Throwable cause) {

        String result = MessageFormat.format(MESSAGE_TEMPLATE, ackRequestString);
        if (null != cause) {
            result += " Cause: " + cause.getMessage();
        }
        return result;
    }

    /**
     * Constructs a new {@code AcknowledgementRequestParseException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static AcknowledgementRequestParseException fromJson(final JsonObject jsonObject,
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

    @NotThreadSafe
    private static class Builder extends DittoRuntimeExceptionBuilder<AcknowledgementRequestParseException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected AcknowledgementRequestParseException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new AcknowledgementRequestParseException(dittoHeaders, message, description, cause, href);
        }

    }

}
