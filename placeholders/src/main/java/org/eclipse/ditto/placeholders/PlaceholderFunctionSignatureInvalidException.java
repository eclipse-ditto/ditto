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
 * Thrown when a {@link PipelineFunction}s signature was invalid (e.g. too many, to few parameters, wrong type,
 * etc.).
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionSignatureInvalidException.ERROR_CODE)
public final class PlaceholderFunctionSignatureInvalidException extends DittoRuntimeException
        implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "function.signature.invalid";

    private static final String MESSAGE_TEMPLATE = "The function signature <fn:{0}{1}> could not be parsed.";

    private static final String DESCRIPTION_TEMPLATE = "Ensure to conform to the function''s signature: <fn:{0}{1}>";

    private static final long serialVersionUID = -260947563924055271L;

    private PlaceholderFunctionSignatureInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a builder of this exception.
     *
     * @param invokedParameters the parameters (incl parentheses) with which the function was invoked.
     * @param pipelineFunction the function whose signature did not match.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<PlaceholderFunctionSignatureInvalidException> newBuilder(
            final String invokedParameters,
            final PipelineFunction pipelineFunction) {
        return new Builder()
                .message(MessageFormat.format(MESSAGE_TEMPLATE, pipelineFunction.getName(), invokedParameters))
                .description(MessageFormat.format(DESCRIPTION_TEMPLATE, pipelineFunction.getName(),
                        pipelineFunction.getSignature()));
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject This exception in JSON format.
     * @param dittoHeaders Ditto headers.
     * @return Deserialized exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PlaceholderFunctionSignatureInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
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
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionSignatureInvalidException> {

        private Builder() {description(DESCRIPTION_TEMPLATE);}

        @Override
        protected PlaceholderFunctionSignatureInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PlaceholderFunctionSignatureInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
