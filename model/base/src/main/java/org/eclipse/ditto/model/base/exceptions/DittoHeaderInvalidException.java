/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.exceptions;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when an (external) header value can not be converted to a Ditto header.
 */
@Immutable
@JsonParsableException(errorCode = DittoHeaderInvalidException.ERROR_CODE)
public final class DittoHeaderInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "header.invalid";

    private static final String DEFAULT_MESSAGE = "The value of a header is invalid.";

    private static final String MESSAGE_TEMPLATE = "The value ''{0}'' of the header ''{1}'' is not a valid {2}.";

    private static final String DEFAULT_DESCRIPTION = "Verify that the header has the correct syntax and try again.";

    private static final String DESCRIPTION_TEMPLATE =
            "Verify that the value of the header ''{0}'' is a valid ''{1}'' and try again.";

    private static final long serialVersionUID = -2338222496153977081L;


    /**
     * Constructs a new {@code DittoRuntimeException} object.
     *
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    private DittoHeaderInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerName the key of the header.
     * @param headerValue the value of the header.
     * @param headerType the expected type of the header. (int, String, entity-tag...)
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DittoHeaderInvalidException.Builder newInvalidTypeBuilder(final String headerName,
            @Nullable final CharSequence headerValue, final String headerType) {

        return new DittoHeaderInvalidException.Builder(headerName, headerValue, headerType);
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerDefinition the definition of the header.
     * @param headerValue the value of the header.
     * @param headerType the type of the header. (int, String, entity-tag...)
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    public static DittoHeaderInvalidException.Builder newInvalidTypeBuilder(final HeaderDefinition headerDefinition,
            @Nullable final CharSequence headerValue, final String headerType) {

        return new DittoHeaderInvalidException.Builder(headerDefinition.getKey(), headerValue, headerType);
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} with a custom message.
     *
     * @param customMessage the custom message
     * @return the builder.
     */
    public static DittoHeaderInvalidException.Builder newCustomMessageBuilder(final String customMessage) {
        return new DittoHeaderInvalidException.Builder(customMessage);
    }

    /**
     * Constructs a new {@code DittoHeaderInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeaderInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DittoHeaderInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * A mutable builder with a fluent API for a {@link DittoHeaderInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoHeaderInvalidException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String headerName, @Nullable final CharSequence headerValue, final String headerType) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(headerValue), requireNonNull(headerName),
                    requireNonNull(headerType)));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, headerName, headerType));
        }

        private Builder(final String customMessage) {
            this();
            message(customMessage);
        }

        @Override
        protected DittoHeaderInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoHeaderInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
