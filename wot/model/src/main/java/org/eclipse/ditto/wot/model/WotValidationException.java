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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.net.URL;
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
 * Thrown if a WoT (Web of Things) ThingModel or ThingDescription was not valid.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableException(errorCode = WotValidationException.ERROR_CODE)
public final class WotValidationException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "validation.error";

    private static final String MESSAGE_TEMPLATE =
            "The WoT TM/TD at URI ''{0}'' contained an invalid element not following the specification.";

    private static final String DEFAULT_DESCRIPTION =
            "Please ensure that the ThingModel or ThingDescription is valid regarding to the WoT (Web of Things) specification.";

    private static final long serialVersionUID = -986554434422221828L;

    private WotValidationException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code WotValidationException} object.
     *
     * @param thingModelUrl the URL of the ThingModel which was not valid.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public WotValidationException(final URL thingModelUrl, final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(thingModelUrl.toString()), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final CharSequence thingModelUrl) {
        checkNotNull(thingModelUrl, "thingModelUrl");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingModelUrl));
    }

    /**
     * A mutable builder for a {@code WotValidationException}.
     *
     * @param thingModelIri the IRI of the ThingModel which was not valid.
     * @return the builder.
     * @throws NullPointerException if {@code thingModelIri} is {@code null}.
     */
    public static Builder newBuilder(final IRI thingModelIri) {
        return new Builder(thingModelIri);
    }

    /**
     * A mutable builder for a {@code WotValidationException}.
     *
     * @param thingModelUrl the URL of the ThingModel which was not valid.
     * @return the builder.
     * @throws NullPointerException if {@code thingModelUrl} is {@code null}.
     */
    public static Builder newBuilder(final URL thingModelUrl) {
        return new Builder(thingModelUrl);
    }

    /**
     * A mutable builder for a {@code WotValidationException}.
     *
     * @param errorMessage a specific error message indicating why the ThingModel was not valid.
     * @return the builder.
     * @throws NullPointerException if {@code errorMessage} is {@code null}.
     */
    public static Builder newBuilder(final String errorMessage) {
        return new Builder(errorMessage);
    }

    /**
     * Constructs a new {@code WotValidationException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static WotValidationException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code WotValidationException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotValidationException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotValidationException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.wot.model.WotValidationException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotValidationException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final IRI thingModelIri) {
            this();
            message(WotValidationException.getMessage(thingModelIri));
        }

        private Builder(final URL thingModelUrl) {
            this();
            message(WotValidationException.getMessage(thingModelUrl.toString()));
        }

        private Builder(final String errorMessage) {
            this();
            message(checkNotNull(errorMessage, "errorMessage"));
        }

        @Override
        protected WotValidationException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotValidationException(dittoHeaders, message, description, cause, href);
        }

    }
}
