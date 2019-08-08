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
@JsonParsableException(errorCode = EntityNamespaceInvalidException.ERROR_CODE)
public final class EntityNamespaceInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "entity.namespace.invalid";

    private static final String MESSAGE_TEMPLATE = "Entity namespace ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "The namespace must be defined in java package notation and it must be " +
                    "a valid URI path segment according to RFC-2396";

    private static final long serialVersionUID = -2026814929325279158L;

    private EntityNamespaceInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code EntityNamespaceInvalidException}.
     *
     * @param entityNamespace the namespace of the entity.
     * @return the builder.
     */
    public static Builder forEntityNamespace(final CharSequence entityNamespace) {
        return new Builder(entityNamespace);
    }

    /**
     * Constructs a new {@code EntityNamespaceInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static EntityNamespaceInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link EntityNamespaceInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<EntityNamespaceInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence entityNamespace) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, entityNamespace));
        }

        @Override
        protected EntityNamespaceInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new EntityNamespaceInvalidException(dittoHeaders, message, description, cause, href);
        }
    }

}
