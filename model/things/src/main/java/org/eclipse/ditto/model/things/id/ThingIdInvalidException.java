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
package org.eclipse.ditto.model.things.id;

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
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if the Thing's ID is not valid (for example if it does not comply to the Thing ID REGEX).
 */
@Immutable
@JsonParsableException(errorCode = ThingIdInvalidException.ERROR_CODE)
public final class ThingIdInvalidException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "id.invalid";

    private static final String MESSAGE_TEMPLATE = "Thing ID ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must contain a namespace prefix (java package notation + a colon ':') + a name and must be a valid " +
                    "URI path segment according to RFC-3986";
    private static final String INVALID_NAMESPACE_DESCRIPTION = "The namespace prefix must conform the syntax of " +
            "the java package notation and must end with a colon (':').";
    private static final String INVALID_NAME_DESCRIPTION = "The name of the thing was not valid. It must be a valid " +
            "URI path segment according to RFC-3986";

    private static final long serialVersionUID = -2026814719409279158L;

    /**
     * Constructs a new {@code ThingIdInvalidException} object.
     *
     * @param thingId the invalid Thing ID.
     */
    public ThingIdInvalidException(final String thingId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, thingId), DEFAULT_DESCRIPTION, null, null);
    }

    private ThingIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingIdInvalidException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence thingId) {
        return new Builder(thingId);
    }

    static Builder forInvalidName(final CharSequence thingId) {
        return new Builder(thingId).description(INVALID_NAME_DESCRIPTION);
    }

    static Builder forInvalidNamespace(final CharSequence thingId) {
        return new Builder(thingId).description(INVALID_NAMESPACE_DESCRIPTION);
    }

    /**
     * Constructs a new {@code ThingIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     */
    public static ThingIdInvalidException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ThingIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static ThingIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingIdInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence thingId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId));
        }

        @Override
        public Builder description(@Nullable final String description) {
            return (Builder) super.description(description);
        }

        @Override
        protected ThingIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ThingIdInvalidException(dittoHeaders, message, description, cause, href);
        }
    }

}
