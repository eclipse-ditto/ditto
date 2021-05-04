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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.base.api.common.CommonCommand;

/**
 * Command for purging arbitrary entities.
 */
@Immutable
@JsonParsableCommand(typePrefix = PurgeEntities.TYPE_PREFIX, name = PurgeEntities.NAME)
public final class PurgeEntities extends CommonCommand<PurgeEntities> implements WithEntityType {

    /**
     * The name of the command.
     */
    static final String NAME = "purgeEntities";

    static final String TYPE_PREFIX = CommonCommand.TYPE_PREFIX;

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final EntityType entityType;
    private final List<EntityId> entityIds;

    private PurgeEntities(final EntityType entityType, final Collection<? extends EntityId> entityIds,
            final DittoHeaders dittoHeaders) {

        super(TYPE, Category.DELETE, dittoHeaders);
        this.entityType = entityType;
        this.entityIds = Collections.unmodifiableList(new ArrayList<>(entityIds));
    }

    /**
     * Returns a new instance of this class.
     *
     * @param entityType the type of the entities to be purged.
     * @param entityIds the IDs of the entities to be purged.
     * @param dittoHeaders the headers of the command.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code entityIds} is empty.
     */
    public static PurgeEntities of(final EntityType entityType, final Collection<? extends EntityId> entityIds,
            final DittoHeaders dittoHeaders) {

        return new PurgeEntities(validateEntityType(entityType), argumentNotEmpty(entityIds, "entityIds"),
                dittoHeaders);
    }

    private static EntityType validateEntityType(final EntityType entityType) {
        return checkNotNull(entityType, "entityType");
    }

    /**
     * Creates a new instance from a JSON object.
     *
     * @param jsonObject the JSON object of which the instance is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     */
    public static PurgeEntities fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<PurgeEntities>(TYPE, jsonObject).deserialize(() -> {
            final EntityType entityType = EntityType.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE));
            final List<EntityId> entityIds = jsonObject.getValueOrThrow(JsonFields.ENTITY_IDS).stream()
                    .map(JsonValue::asString)
                    .map(entityId -> EntityId.of(entityType, entityId))
                    .collect(Collectors.toList());

            return of(entityType, entityIds, dittoHeaders);
        });
    }

    public static String getTopic(final EntityType entityType) {
        return TYPE + ':' + validateEntityType(entityType);
    }

    @Override
    public EntityType getEntityType() {
        return entityType;
    }

    /**
     * Return the IDs of the entities to be purged.
     *
     * @return the entity IDs
     */
    public List<EntityId> getEntityIds() {
        return entityIds;
    }

    @Override
    public PurgeEntities setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(entityType, entityIds, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PurgeEntities;
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
        final PurgeEntities that = (PurgeEntities) o;
        return Objects.equals(entityType, that.entityType) && Objects.equals(entityIds, that.entityIds);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> aPredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(aPredicate);

        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, entityType.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_IDS, JsonArray.of(entityIds), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityType, entityIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", entityType=" + entityType +
                ", entityIds=" + entityIds +
                "]";
    }

    /**
     * This class contains definitions for all specific fields of this command's JSON representation.
     */
    @Immutable
    static final class JsonFields extends Command.JsonFields {

        /**
         * The entityType.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType",
                        FieldType.REGULAR, JsonSchemaVersion.V_2);
        /**
         * The entityIDs.
         */
        public static final JsonFieldDefinition<JsonArray> ENTITY_IDS =
                JsonFactory.newJsonArrayFieldDefinition("entityIds",
                        FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
