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
package org.eclipse.ditto.signals.commands.cleanup;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link CleanupPersistence} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CleanupPersistenceResponse.TYPE)
public final class CleanupPersistenceResponse
        extends AbstractCommandResponse<CleanupPersistenceResponse> implements CleanupCommandResponse<CleanupPersistenceResponse> {

    /**
     * The type of the {@code CleanupCommandResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + CleanupPersistence.NAME;

    private final String entityId;

    private CleanupPersistenceResponse(final String entityId, final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.entityId = ConditionChecker.checkNotNull(entityId, "entityId");
    }

    /**
     * Returns a CleanupPersistenceResponse for a successfully cleanup {@code entityId}.
     *
     * @param entityId the entity's ID which should be cleaned up.
     * @param dittoHeaders the headers of the response.
     * @return a command response for cleanupPersistence.
     */
    public static CleanupPersistenceResponse success(final String entityId, final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(entityId, HttpStatusCode.OK, dittoHeaders);
    }

    /**
     * Returns a CleanupPersistenceResponse for a failed cleanup {@code entityId}.
     *
     * @param entityId the entity's ID which should be cleaned up.
     * @param dittoHeaders the headers of the response.
     * @return a command response for cleanupPersistence.
     */
    public static CleanupPersistenceResponse failure(final String entityId, final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(entityId, HttpStatusCode.INTERNAL_SERVER_ERROR, dittoHeaders);
    }

    @Override
    public String getEntityId() {
        return entityId;
    }

    @Override
    public CleanupPersistenceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CleanupPersistenceResponse(this.getId(), this.getStatusCode(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(CleanupCommandResponse.JsonFields.ENTITY_ID, entityId, predicate);
    }

    @Override
    public String getId() {
        return getEntityId();
    }

    /**
     * Creates a new {@code CleanupPersistenceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the CleanupPersistenceResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link Command.JsonFields#ID}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CleanupPersistenceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<CleanupPersistenceResponse>(TYPE, jsonObject).deserialize(statusCode ->
                new CleanupPersistenceResponse(jsonObject.getValueOrThrow(CleanupCommandResponse.JsonFields.ENTITY_ID),
                        statusCode,
                        dittoHeaders)
        );
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
        final CleanupPersistenceResponse that = (CleanupPersistenceResponse) o;
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
