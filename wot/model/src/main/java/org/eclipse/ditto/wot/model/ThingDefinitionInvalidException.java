/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

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
 * Thrown if a Ditto Thing or Feature did not contain a Definition containing a URL potentially pointing to a
 * WoT ThingModel URL.
 * @since 2.4.0
 */
@Immutable
@JsonParsableException(errorCode = ThingDefinitionInvalidException.ERROR_CODE)
public final class ThingDefinitionInvalidException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "definition.invalid";

    private static final String MISSING_DEFINITION_MESSAGE =
            "The definition identifier is missing from the Thing or Feature.";

    private static final String EXISTING_DEFINITION_MESSAGE_TEMPLATE =
            "The definition identifier ''{0}'' is not a valid URL.";

    private static final String DEFAULT_DESCRIPTION =
            "Please add a URL pointing to a WoT (Web of Things) ThingModel from the Thing or Feature in order to " +
                    "make use of the WoT integration.";

    private static final long serialVersionUID = 982222765628899021L;

    private ThingDefinitionInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code ThingDefinitionInvalidException} object.
     *
     * @param definition the Thing or Feature definition or {@code null}.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if the {@code dittoHeaders} were {@code null}.
     */
    public ThingDefinitionInvalidException(@Nullable final CharSequence definition, final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(definition), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(@Nullable final CharSequence definition) {
        if (null != definition) {
            return MessageFormat.format(EXISTING_DEFINITION_MESSAGE_TEMPLATE, String.valueOf(definition));
        } else {
            return MISSING_DEFINITION_MESSAGE;
        }
    }

    /**
     * A mutable builder for a {@code ThingDefinitionInvalidException}.
     *
     * @param definition the Thing/Feature definition identifier.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    public static Builder newBuilder(@Nullable final CharSequence definition) {
        return new Builder(definition);
    }

    /**
     * Constructs a new {@code ThingDefinitionInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingDefinitionInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ThingDefinitionInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ThingDefinitionInvalidException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingDefinitionInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ThingDefinitionInvalidException fromJson(final JsonObject jsonObject,
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

    /**
     * A mutable builder with a fluent API for a {@link ThingDefinitionInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingDefinitionInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final CharSequence definition) {
            this();
            message(ThingDefinitionInvalidException.getMessage(definition));
        }

        @Override
        protected ThingDefinitionInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ThingDefinitionInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
