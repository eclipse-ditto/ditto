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
package org.eclipse.ditto.things.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.id.EntityIdInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if the Thing's ID is not valid (for example if it does not comply to the Thing ID REGEX).
 */
@Immutable
@JsonParsableException(errorCode = ThingIdInvalidException.ERROR_CODE)
public final class ThingIdInvalidException extends EntityIdInvalidException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "id.invalid";

    private static final String MESSAGE_TEMPLATE = "Thing ID ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must conform to the namespaced entity ID notation (see Ditto documentation)";

    private static final URI DEFAULT_HREF =
            URI.create("https://www.eclipse.org/ditto/basic-namespaces-and-names.html#namespaced-id");

    private static final long serialVersionUID = -2026814719409279158L;

    /**
     * Constructs a new {@code ThingIdInvalidException} object.
     *
     * @param thingId the invalid Thing ID.
     */
    public ThingIdInvalidException(final String thingId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, thingId), DEFAULT_DESCRIPTION, null,
                DEFAULT_HREF);
    }

    private ThingIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingIdInvalidException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code ThingIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ThingIdInvalidException fromMessage(@Nullable final String message, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ThingIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ThingIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link ThingIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingIdInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
            href(DEFAULT_HREF);
        }

        private Builder(@Nullable final CharSequence thingId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId));
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
