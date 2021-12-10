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
package org.eclipse.ditto.protocol.mappingstrategies;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.protocol.Adaptable;

/**
 * This exception is thrown to indicate that a particular {@link Adaptable}
 * is illegal in a certain context.
 *
 * @since 2.3.0
 */
@JsonParsableException(errorCode = IllegalAdaptableException.ERROR_CODE)
public final class IllegalAdaptableException extends DittoRuntimeException {

    /**
     * Error code of {@code IllegalAdaptableException}.
     */
    static final String ERROR_CODE = "things.protocol.adapter:adaptable.illegal";

    private static final HttpStatus HTTP_STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    private static final String DEFAULT_DESCRIPTION = "Please ensure that the Adaptable matches the expectations.";

    private static final long serialVersionUID = 1552465013185426687L;

    private IllegalAdaptableException(final String errorCode,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(errorCode, httpStatus, dittoHeaders, message, description, cause, href);
    }

    /**
     * Returns a new instance of {@code IllegalAdaptableException} for
     * the specified {@code Adaptable} argument.
     *
     * @param message the detail message of the exception.
     * @param dittoHeaders the {@code DittoHeaders} of the exception.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     */
    public IllegalAdaptableException(final String message, final DittoHeaders dittoHeaders) {
        this(ERROR_CODE, HTTP_STATUS, dittoHeaders, message, DEFAULT_DESCRIPTION, null, null);
    }

    /**
     * Returns a new instance of {@code IllegalAdaptableException} for
     * the specified {@code Adaptable} argument.
     *
     * @param message the detail message of the exception.
     * @param description the description of the exception.
     * @param dittoHeaders the {@code DittoHeaders} of the exception.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     */
    public IllegalAdaptableException(final String message,
            @Nullable final String description,
            final DittoHeaders dittoHeaders) {

        this(ERROR_CODE, HTTP_STATUS, dittoHeaders, message, description, null, null);
    }

    /**
     * Returns a new instance of {@code IllegalAdaptableException} for
     * the specified {@code Adaptable} argument.
     *
     * @param message the detail message of the exception.
     * @param description the description of the exception.
     * @param cause the cause of the exception.
     * @param dittoHeaders the {@code DittoHeaders} of the exception.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     */
    public IllegalAdaptableException(final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            final DittoHeaders dittoHeaders) {

        this(ERROR_CODE, HTTP_STATUS, dittoHeaders, message, description, cause, null);
    }

    /**
     * Deserializes the specified {@code JsonObject} argument to an {@code IllegalAdaptableException}.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @param dittoHeaders the headers of the deserialized exception.
     * @return the new UnsupportedSignalException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all mandatory
     * fields.
     * @throws JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static IllegalAdaptableException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        checkNotNull(jsonObject, "jsonObject");
        try {
            return new IllegalAdaptableException(
                    deserializeErrorCode(jsonObject),
                    deserializeHttpStatus(jsonObject),
                    dittoHeaders,
                    jsonObject.getValue(JsonFields.MESSAGE).orElse(null),
                    jsonObject.getValue(JsonFields.DESCRIPTION).orElse(null),
                    null,
                    deserializeHref(jsonObject).orElse(null)
            );
        } catch (final Exception e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize JSON object to a {0}: {1}",
                            IllegalAdaptableException.class.getSimpleName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

    private static String deserializeErrorCode(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.ERROR_CODE;
        final String result = jsonObject.getValueOrThrow(fieldDefinition);
        if (!ERROR_CODE.equals(result)) {
            throw new JsonParseException(MessageFormat.format(
                    "Error code <{0}> of field <{1}> differs from the expected <{2}>.",
                    result,
                    fieldDefinition.getPointer(),
                    ERROR_CODE
            ));
        }
        return result;
    }

    private static HttpStatus deserializeHttpStatus(final JsonObject jsonObject) {
        final JsonFieldDefinition<Integer> fieldDefinition = JsonFields.STATUS;
        final Integer statusCode = jsonObject.getValueOrThrow(fieldDefinition);
        if (!Objects.equals(HTTP_STATUS.getCode(), statusCode)) {
            throw new JsonParseException(MessageFormat.format(
                    "HTTP status code <{0}> of field <{1}> differs from the expected <{2}>.",
                    statusCode,
                    fieldDefinition.getPointer(),
                    HTTP_STATUS.getCode()
            ));
        } else {
            return HTTP_STATUS;
        }
    }

    private static Optional<URI> deserializeHref(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.HREF;
        try {
            return jsonObject.getValue(fieldDefinition).map(URI::create);
        } catch (final IllegalArgumentException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Syntax of link URI of field <{0}> is invalid: {1}",
                            fieldDefinition.getPointer(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new IllegalAdaptableException(getErrorCode(),
                getHttpStatus(),
                dittoHeaders,
                getMessage(),
                getDescription().orElse(null),
                getCause(),
                getHref().orElse(null));
    }

}
