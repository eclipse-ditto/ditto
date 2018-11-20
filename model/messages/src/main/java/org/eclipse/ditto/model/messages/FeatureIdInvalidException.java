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
package org.eclipse.ditto.model.messages;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if the a Feature ID was missing when it was expected or did not match an expected feature ID.
 */
@Immutable
public final class FeatureIdInvalidException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "featureid.invalid";

    private static final String MESSAGE_TEMPLATE = "The feature ID was missing or did not match the expected one.";

    private static final String DEFAULT_DESCRIPTION =
            "Please ensure that you added the feature ID to the Message and that this feature ID matches the one " +
                    "from the wrapping MessageCommand.";

    private static final long serialVersionUID = -6221810313469273592L;

    /**
     * Constructs a new {@code FeatureIdInvalidException} object.
     */
    public FeatureIdInvalidException() {
        this(DittoHeaders.empty(), MESSAGE_TEMPLATE, DEFAULT_DESCRIPTION, null, null);
    }

    private FeatureIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code FeatureIdInvalidException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code FeatureIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureIdInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static FeatureIdInvalidException fromMessage(@Nullable final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code FeatureIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureIdInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static FeatureIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link FeatureIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<FeatureIdInvalidException> {

        private Builder() {
            message(MESSAGE_TEMPLATE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected FeatureIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new FeatureIdInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
