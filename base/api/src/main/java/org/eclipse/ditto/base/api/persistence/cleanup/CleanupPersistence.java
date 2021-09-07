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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command for starting the cleanup (deleting stale journal-entries + snapshots) of persistence actors.
 */
@Immutable
@JsonParsableCommand(typePrefix = CleanupCommand.TYPE_PREFIX, name = CleanupPersistence.NAME)
public final class CleanupPersistence
        extends AbstractCommand<CleanupPersistence> implements CleanupCommand<CleanupPersistence> {

    /**
     * The name of the {@code CleanupCommand}.
     */
    static final String NAME = "cleanupPersistence";

    /**
     * The type of the {@code CleanupCommand}.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final EntityId entityId;

    private CleanupPersistence(final EntityId entityId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.entityId = ConditionChecker.checkNotNull(entityId, "entityId");
    }

    /**
     * Creates a new CleanupPersistence command for starting cleanup in persistence actor of the passed
     * {@code entityId}.
     *
     * @param entityId the entity ID to cleanup snapshots and journal entries for in the database.
     * @param dittoHeaders the headers of the command.
     * @return a command for cleaning up persistence.
     */
    public static CleanupPersistence of(final EntityId entityId, final DittoHeaders dittoHeaders) {
        return new CleanupPersistence(entityId, dittoHeaders);
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public CleanupPersistence setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CleanupPersistence(entityId, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(CleanupCommand.JsonFields.ENTITY_TYPE, entityId.getEntityType().toString());
        jsonObjectBuilder.set(CleanupCommand.JsonFields.ENTITY_ID, String.valueOf(entityId), predicate);
    }

    /**
     * Creates a new {@code CleanupPersistence} command from the given JSON object.
     *
     * @param jsonObject the JSON object of which the CleanupPersistence is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link CleanupCommand.JsonFields#ENTITY_ID}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CleanupPersistence fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CleanupPersistence>(TYPE, jsonObject)
                .deserialize(() -> of(deserializeEntityId(jsonObject), dittoHeaders));
    }

    private static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                CleanupCommand.JsonFields.ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, CleanupCommand.JsonFields.ENTITY_TYPE));
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CleanupPersistence;
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
        final CleanupPersistence cleanupPersistence = (CleanupPersistence) o;
        return entityId.equals(cleanupPersistence.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", entityId=" + entityId +
                "]";
    }

}
