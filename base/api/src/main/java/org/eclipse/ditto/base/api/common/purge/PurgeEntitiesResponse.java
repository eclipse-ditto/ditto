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
package org.eclipse.ditto.base.api.common.purge;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Response to {@link PurgeEntities}.
 */
@Immutable
@JsonParsableCommandResponse(type = PurgeEntitiesResponse.TYPE)
public final class PurgeEntitiesResponse extends CommonCommandResponse<PurgeEntitiesResponse> {

    /**
     * The type of the response.
     */
    public static final String TYPE = TYPE_PREFIX + PurgeEntities.NAME;

    private static final CommandResponseJsonDeserializer<PurgeEntitiesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new PurgeEntitiesResponse(
                                EntityType.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE)),
                                jsonObject.getValueOrThrow(JsonFields.SUCCESSFUL),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final EntityType entityType;
    private final boolean successful;

    private PurgeEntitiesResponse(final EntityType entityType,
            final boolean successful,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Arrays.asList(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR),
                        PurgeEntitiesResponse.class),
                dittoHeaders);
        this.entityType = checkNotNull(entityType);
        this.successful = successful;
    }

    /**
     * Returns an instance which indicates a successful purge.
     *
     * @param entityType type of the entities represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a successful purge.
     */
    public static PurgeEntitiesResponse successful(final EntityType entityType, final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, true, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Returns an instance which indicates that a failed purge.
     *
     * @param entityType type of the entities represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a failed purge.
     */
    public static PurgeEntitiesResponse failed(final EntityType entityType, final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, false, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders);
    }

    /**
     * Creates a new instance from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @param headers the headers.
     * @return the deserialized response.
     */
    public static PurgeEntitiesResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return JSON_DESERIALIZER.deserialize(jsonObject, headers);
    }

    /**
     * The type of the entities.
     *
     * @return the type of the entities.
     */
    public EntityType getEntityType() {
        return entityType;
    }

    /**
     * Indicates whether the entities were purged successfully.
     *
     * @return {@code true}, if successful.
     */
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public PurgeEntitiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, successful, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final var that = (PurgeEntitiesResponse) o;
        return Objects.equals(entityType, that.entityType) && successful == that.successful;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PurgeEntitiesResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityType, successful);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> aPredicate) {

        final var predicate = schemaVersion.and(aPredicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, entityType.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUCCESSFUL, successful, predicate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", entityType=" + entityType +
                ", successful=" + successful +
                "]";
    }

    /**
     * Fields of this response.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * The type of the entities affected by this response.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFieldDefinition.ofString("entityType", FieldType.REGULAR, JsonSchemaVersion.V_2);
        /**
         * This JSON field indicates whether the entities were purged successfully.
         */
        public static final JsonFieldDefinition<Boolean> SUCCESSFUL =
                JsonFieldDefinition.ofBoolean("successful", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
