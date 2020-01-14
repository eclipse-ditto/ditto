/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Thrown if the namespaced entity ID is not valid according to
 * {@link org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
 */
@Immutable
@JsonParsableException(errorCode = NamespacedEntityIdInvalidException.ERROR_CODE)
public final class NamespacedEntityIdInvalidException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "namespacedentity.id.invalid";

    private static final String MESSAGE_TEMPLATE = "Namespaced entity ID ''{0}'' is not valid!";

    private static final String NAMESPACED_ENTITY_ID_DESCRIPTION =
            "It must conform to the namespaced entity ID notation (see Ditto documentation) with a maximum length of " +
                    "256 characters.";

    private static final URI DEFAULT_HREF = URI.create("https://www.eclipse.org/ditto/basic-namespaces-and-names.html#namespaced-id");

    private final CharSequence entityId;

    private NamespacedEntityIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            @Nullable final CharSequence entityId) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.entityId = entityId;
    }

    /**
     * A mutable builder for a {@code NamespacedEntityIdInvalidException}.
     *
     * @param entityId the ID of the entity.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence entityId) {
        return new Builder(entityId, NAMESPACED_ENTITY_ID_DESCRIPTION);
    }

    /**
     * Constructs a new {@code NamespacedEntityIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static NamespacedEntityIdInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder(readEntityId(jsonObject).orElse(null))
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(""))
                .href(readHRef(jsonObject).orElse(DEFAULT_HREF))
                .build();
    }

    private static Optional<String> readEntityId(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.getValue(JsonFields.ENTITY_ID);
    }

    public Optional<CharSequence> getEntityId() {
        return Optional.ofNullable(entityId);
    }

    /**
     * A mutable builder with a fluent API for a {@link NamespacedEntityIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<NamespacedEntityIdInvalidException> {

        private final CharSequence entityId;

        private Builder(@Nullable final CharSequence entityId) {
            this.entityId = entityId;
            message(MessageFormat.format(MESSAGE_TEMPLATE, entityId));
            href(DEFAULT_HREF);
        }

        private Builder(@Nullable final CharSequence entityId, final String description) {
            this(entityId);
            description(description);
        }

        @Override
        protected NamespacedEntityIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new NamespacedEntityIdInvalidException(dittoHeaders, message, description, cause, href, entityId);
        }

    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@code DittoRuntimeException}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the HTTP status code of the exception.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.HIDDEN, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
