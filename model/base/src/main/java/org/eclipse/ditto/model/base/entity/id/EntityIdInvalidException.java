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
package org.eclipse.ditto.model.base.entity.id;

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
 * Thrown if the Thing's ID is not valid (for example if it does not comply to the Thing ID REGEX).
 */
@Immutable
@JsonParsableException(errorCode = EntityIdInvalidException.ERROR_CODE)
public final class EntityIdInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "entity.id.invalid";

    private static final String MESSAGE_TEMPLATE = "Entity ID ''{0}'' is not valid!";

    private static final String ENTITY_ID_DESCRIPTION = "It must not be empty";

    private static final String NAMESPACED_ENTITY_ID_DESCRIPTION =
            "It must contain a namespace prefix (java package notation + a colon ':') + ID and must be a valid URI " +
                    "path segment according to RFC-2396";

    private static final long serialVersionUID = -2426810319409279256L;

    private EntityIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code EntityIdInvalidException}.
     *
     * @param entityId the ID of the entity.
     * @return the builder.
     */
    public static Builder forNamespacedEntityId(@Nullable final CharSequence entityId) {
        return new Builder(entityId, NAMESPACED_ENTITY_ID_DESCRIPTION);
    }

    /**
     * A mutable builder for a {@code EntityIdInvalidException}.
     *
     * @param entityId the ID of the entity.
     * @return the builder.
     */
    public static Builder forEntityId(@Nullable final CharSequence entityId) {
        return new Builder(entityId, ENTITY_ID_DESCRIPTION);
    }

    /**
     * Constructs a new {@code ThingIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static EntityIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(""))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link EntityIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<EntityIdInvalidException> {

        private Builder() {
        }

        private Builder(@Nullable final CharSequence entityId, final String description) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, entityId));
            description(description);
        }

        @Override
        protected EntityIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new EntityIdInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
