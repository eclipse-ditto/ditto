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
import javax.annotation.concurrent.Immutable;
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


@Immutable
@JsonParsableException(errorCode = AttributeNotAccessibleException.ERROR_CODE)
public final class AttributeNotAccessibleException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "attribute.notfound";

    private static final String MESSAGE_TEMPLATE = "The attribute with key ''{0}'' on the thing with ID ''{1}'' could "
            + "not be found or the requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the thing and the key of your requested attribute was correct and you have sufficient "
                    + "permissions.";

    private static final long serialVersionUID = 2326664361301515934L;

    private AttributeNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code AttributeNotAccessibleException}.
     *
     * @param thingId the ID of the thing.
     * @param jsonPointer the JSON pointer of the attribute.
     * @return the builder.
     */
    public static Builder newBuilder(final ThingId thingId, final JsonPointer jsonPointer) {
        return new Builder(thingId, jsonPointer);
    }

    /**
     * Constructs a new {@code AttributeNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AttributeNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static AttributeNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code AttributeNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new AttributeNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static AttributeNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link AttributeNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AttributeNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ThingId thingId, final JsonPointer attributeKey) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, attributeKey, String.valueOf(thingId)));
        }

        @Override
        protected AttributeNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new AttributeNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }


}
