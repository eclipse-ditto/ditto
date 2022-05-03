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
package org.eclipse.ditto.base.model.exceptions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Thrown if an Entity was not allowed to be created via configuration.
 */
@Immutable
@JsonParsableException(errorCode = EntityNotCreatableException.ERROR_CODE)
public final class EntityNotCreatableException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "entity.notcreatable";

    static final String MESSAGE_TEMPLATE = "The Entity with ID ''{0}'' could not be created as the creation was " +
            "configured not to be allowed.";

    static final String DEFAULT_DESCRIPTION =
            "The creation was configured not to be allowed for the authenticated subject. Check with your " +
                    "administrator if that was unexpected.";

    private static final long serialVersionUID = 8372510019176456321L;

    private final String entityId;

    private EntityNotCreatableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            @Nullable final CharSequence entityId) {

        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
        this.entityId = null != entityId ? entityId.toString() : null;
    }

    /**
     * A mutable builder for a {@code EntityNotCreatableException} thrown if an Entity could not be created.
     *
     * @param entityId the ID of the Entity.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence entityId) {
        return new Builder(entityId);
    }

    /**
     * Constructs a new {@code EntityNotCreatableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static EntityNotCreatableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromJson(jsonObject,
                dittoHeaders,
                new EntityNotCreatableException.Builder(readEntityId(jsonObject).orElse(null)));
    }

    private static Optional<String> readEntityId(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.getValue(JsonFields.ENTITY_ID);
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JsonFields.ENTITY_ID, entityId, predicate);
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder(null)
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final EntityNotCreatableException that = (EntityNotCreatableException) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId);
    }

    /**
     * A mutable builder with a fluent API for a {@link EntityNotCreatableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<EntityNotCreatableException> {

        @Nullable private final CharSequence entityId;

        private Builder(@Nullable final CharSequence entityId) {
            this.entityId = entityId;
            message(MessageFormat.format(MESSAGE_TEMPLATE, entityId));
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected EntityNotCreatableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new EntityNotCreatableException(dittoHeaders, message, description, cause, href, entityId);
        }

    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@code EntityNotCreatableException}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.HIDDEN, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
