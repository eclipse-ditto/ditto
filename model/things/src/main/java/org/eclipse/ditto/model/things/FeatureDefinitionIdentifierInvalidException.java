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
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception is thrown if an Identifier of a Feature Definition has an invalid structure.
 */
public final class FeatureDefinitionIdentifierInvalidException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "feature.definition.identifier.invalid";

    private static final String MESSAGE_TEMPLATE = "Feature Definition Identifier <{0}> is invalid!";

    private static final String DEFAULT_DESCRIPTION = "An Identifier string is expected to have the structure " +
            "'namespace:name:version'. Each segment must contain at least one char of [_a-zA-Z0-9\\-.]";

    private static final long serialVersionUID = -5652551484675928573L;

    /**
     * Constructs a new {@code FeatureDefinitionIdentifierInvalidException} object.
     *
     * @param identifierAsCharSequence a char sequence representing the invalid FeatureDefinition Identifier.
     */
    public FeatureDefinitionIdentifierInvalidException(final CharSequence identifierAsCharSequence) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, identifierAsCharSequence),
                DEFAULT_DESCRIPTION, null, null);
    }

    private FeatureDefinitionIdentifierInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code FeatureDefinitionIdentifierInvalidException}.
     *
     * @param identifierAsCharSequence a char sequence representing the invalid FeatureDefinition Identifier.
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence identifierAsCharSequence) {
        return new Builder(identifierAsCharSequence);
    }

    /**
     * Constructs a new {@code FeatureDefinitionIdentifierInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureDefinitionIdentifierInvalidException.
     */
    public static FeatureDefinitionIdentifierInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code FeatureDefinitionIdentifierInvalidException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new FeatureDefinitionIdentifierInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the
     * {@link JsonFields#MESSAGE} field.
     */
    public static FeatureDefinitionIdentifierInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(DittoRuntimeException.readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for an immutable {@code FeatureDefinitionIdentifierInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends
            DittoRuntimeExceptionBuilder<FeatureDefinitionIdentifierInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence identifierAsCharSequence) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, identifierAsCharSequence));
        }

        @Override
        protected FeatureDefinitionIdentifierInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new FeatureDefinitionIdentifierInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
