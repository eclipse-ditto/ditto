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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Exception which indicates that an attribute cannot be modified.
 */
@JsonParsableException(errorCode = AttributeNotModifiableException.ERROR_CODE)
public final class AttributeNotModifiableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "attribute.notmodifiable";

    private static final String MESSAGE_TEMPLATE = " The Attribute with name ''{0}'' on the Thing with ID ''{1}'' "
            + "could not be modified as the requester "
            + "had insufficient permissions ('WRITE' is required).";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested Thing was correct and you have sufficient permissions.";

    private static final long serialVersionUID = 2892397125359904460L;

    private AttributeNotModifiableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code AttributeNotModifiableException}.
     *
     * @param thingId the ID of the thing.
     * @param attributePath the path of the attribute which is not modifiable.
     * @return the builder.
     */
    public static Builder newBuilder(final ThingId thingId,
            final JsonPointer attributePath) {
        return new Builder(thingId, attributePath);
    }

    /**
     * Constructs a new {@code AttributeNotModifiableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AttributeNotModifiableException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static AttributeNotModifiableException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code AttributeNotModifiableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AttributeNotModifiableException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static AttributeNotModifiableException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link AttributeNotModifiableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AttributeNotModifiableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ThingId thingId, final JsonPointer attributePath) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, attributePath.toString(), thingId));
        }

        @Override
        protected AttributeNotModifiableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new AttributeNotModifiableException(dittoHeaders, message, description, cause, href);
        }
    }
}
