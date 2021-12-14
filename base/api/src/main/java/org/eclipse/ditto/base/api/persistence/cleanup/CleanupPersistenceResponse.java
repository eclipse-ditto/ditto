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
package org.eclipse.ditto.base.api.persistence.cleanup;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Response to a {@link CleanupPersistence} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CleanupPersistenceResponse.TYPE)
public final class CleanupPersistenceResponse extends AbstractCommandResponse<CleanupPersistenceResponse>
        implements CleanupCommandResponse<CleanupPersistenceResponse> {

    /**
     * The type of the {@code CleanupCommandResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + CleanupPersistence.NAME;

    private static final CommandResponseJsonDeserializer<CleanupPersistenceResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();

                        final var entityType = EntityTypeJsonDeserializer.deserializeEntityType(jsonObject,
                                CleanupCommandResponse.JsonFields.ENTITY_TYPE);
                        final var entityId = EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                                CleanupCommandResponse.JsonFields.ENTITY_ID,
                                entityType);

                        return new CleanupPersistenceResponse(entityId,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });


    private final EntityId entityId;

    private CleanupPersistenceResponse(final EntityId entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Arrays.asList(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR),
                        CleanupPersistenceResponse.class),
                dittoHeaders);
        this.entityId = ConditionChecker.checkNotNull(entityId, "entityId");
    }

    /**
     * Returns a CleanupPersistenceResponse for a successfully cleanup {@code entityId}.
     *
     * @param entityId the entity's ID which should be cleaned up.
     * @param dittoHeaders the headers of the response.
     * @return a command response for cleanupPersistence.
     */
    public static CleanupPersistenceResponse success(final EntityId entityId, final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(entityId, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Returns a CleanupPersistenceResponse for a failed cleanup {@code entityId}.
     *
     * @param entityId the entity's ID which should be cleaned up.
     * @param dittoHeaders the headers of the response.
     * @return a command response for cleanupPersistence.
     */
    public static CleanupPersistenceResponse failure(final EntityId entityId, final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(entityId, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public CleanupPersistenceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(getEntityId(), getHttpStatus(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(CleanupCommandResponse.JsonFields.ENTITY_TYPE, entityId.getEntityType().toString());
        jsonObjectBuilder.set(CleanupCommandResponse.JsonFields.ENTITY_ID, String.valueOf(entityId), predicate);
    }

    /**
     * Creates a new {@code CleanupPersistenceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the CleanupPersistenceResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link org.eclipse.ditto.base.model.signals.commands.Command.JsonFields#TYPE}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CleanupPersistenceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
        final var that = (CleanupPersistenceResponse) o;
        return entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CleanupPersistenceResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", entityId=" + entityId +
                "]";
    }

}
