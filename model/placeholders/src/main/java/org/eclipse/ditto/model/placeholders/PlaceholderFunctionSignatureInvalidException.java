/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders;

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
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when a {@link PipelineFunction}s signature was invalid (e.g. too many, to few parameters, wrong type,
 * etc.).
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderFunctionSignatureInvalidException.ERROR_CODE)
public final class PlaceholderFunctionSignatureInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "placeholder.function.signature.invalid";

    private static final String MESSAGE_TEMPLATE = "The function signature <fn:{0}{1}> could not be parsed.";

    private static final String DESCRIPTION_TEMPLATE = "Ensure to conform to the function''s signature: <fn:{0}{1}>";

    private static final long serialVersionUID = -260947563924055271L;

    private PlaceholderFunctionSignatureInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static PlaceholderFunctionSignatureInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(jsonObject.getValueOrThrow(JsonFields.MESSAGE))
                .description(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(DESCRIPTION_TEMPLATE))
                .build();
    }

    /**
     * Returns a mutable builder for this exception.
     *
     * @return the builder.
     */
    public DittoRuntimeExceptionBuilder<PlaceholderFunctionSignatureInvalidException> toBuilder() {
        return new Builder()
                .dittoHeaders(getDittoHeaders())
                .message(getMessage())
                .description(getDescription().orElse(DESCRIPTION_TEMPLATE))
                .cause(getCause())
                .href(getHref().orElse(null));
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder
            extends DittoRuntimeExceptionBuilder<PlaceholderFunctionSignatureInvalidException> {

        private Builder() {}

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
