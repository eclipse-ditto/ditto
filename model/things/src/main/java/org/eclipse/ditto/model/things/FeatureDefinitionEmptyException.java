/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception is thrown if a Feature Definition was set to an empty array.
 */
public final class FeatureDefinitionEmptyException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "feature.definition.empty";

    private static final String MESSAGE_TEMPLATE = "Feature Definition must not be empty!";

    private static final String DEFAULT_DESCRIPTION = "A Feature Definition must contain at least one element. It can " +
            "however also be set to null or deleted completely.";

    private static final long serialVersionUID = -3812521480675928569L;

    /**
     * Constructs a new {@code FeatureDefinitionEmptyException} object.
     */
    public FeatureDefinitionEmptyException() {
        this(DittoHeaders.empty(), MESSAGE_TEMPLATE, DEFAULT_DESCRIPTION, null, null);
    }

    private FeatureDefinitionEmptyException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code FeatureDefinitionEmptyException}.
     *
     *  @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code FeatureDefinitionEmptyException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureDefinitionEmptyException.
     */
    public static FeatureDefinitionEmptyException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code FeatureDefinitionEmptyException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureDefinitionEmptyException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the
     * {@link JsonFields#MESSAGE} field.
     */
    public static FeatureDefinitionEmptyException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(DittoRuntimeException.readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for an immutable {@code FeatureDefinitionEmptyException}.
     */
    @NotThreadSafe
    public static final class Builder extends
            DittoRuntimeExceptionBuilder<FeatureDefinitionEmptyException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
            message(MESSAGE_TEMPLATE);
        }

        @Override
        protected FeatureDefinitionEmptyException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new FeatureDefinitionEmptyException(dittoHeaders, message, description, cause, href);
        }

    }

}
