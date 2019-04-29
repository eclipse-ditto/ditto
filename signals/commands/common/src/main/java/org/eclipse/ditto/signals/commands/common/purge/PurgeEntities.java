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
package org.eclipse.ditto.signals.commands.common.purge;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.common.CommonCommand;

/**
 * Command for purging arbitrary entities.
 */
@Immutable
@JsonParsableCommand(typePrefix = PurgeEntities.TYPE_PREFIX, name = PurgeEntities.NAME)
public final class PurgeEntities extends CommonCommand<PurgeEntities> {

    /**
     * The name of the command.
     */
    static final String NAME = "purgeEntities";

    static final String TYPE_PREFIX = CommonCommand.TYPE_PREFIX;

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String entityType;
    private final List<String> entityIds;

    private PurgeEntities(final CharSequence entityType,
            final Iterable<String> entityIds, final DittoHeaders dittoHeaders) {
        super(TYPE, Category.DELETE, dittoHeaders);

        checkNotNull(entityType);
        checkNotNull(entityIds);
        if (!entityIds.iterator().hasNext()) {
            throw new IllegalArgumentException("entityIds must not be empty");
        }

        this.entityType = entityType.toString();

        final List<String> entityIdsList = new ArrayList<>();
        entityIds.forEach(entityIdsList::add);
        this.entityIds = Collections.unmodifiableList(entityIdsList);
    }

    /**
     * Returns a new instance.
     *
     * @param entityType the type of the entities to be purged.
     * @param entityIds the IDs of the entities to be purged
     * @param dittoHeaders the headers of the command.
     * @return the instance.
     */
    public static PurgeEntities of(final CharSequence entityType,
            final Iterable<String> entityIds, final DittoHeaders dittoHeaders) {
        return new PurgeEntities(entityType, entityIds, dittoHeaders);
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
            final String entityType = jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE);
            final List<String> entityIds = jsonObject.getValueOrThrow(JsonFields.ENTITY_IDS).stream()
                    .map(JsonValue::asString)
                    .collect(Collectors.toList());

            return new PurgeEntities(entityType, entityIds, dittoHeaders);
        });
    }

    public static String getTopic(final String entityType) {
        requireNonNull(entityType);

        return TYPE + ':' + entityType;
    }

    /**
     * Returns the type of the entities to be purged.
     *
     * @return the type of the entities
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Return the IDs of the entities to be purged.
     *
     * @return the entity IDs
     */
    public List<String> getEntityIds() {
        return entityIds;
    }

    @Override
    public PurgeEntities setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PurgeEntities(entityType, entityIds, dittoHeaders);
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
        return Objects.equals(entityType, that.entityType) &&
                Objects.equals(entityIds, that.entityIds);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> aPredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(aPredicate);

        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, entityType, predicate);
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
                        FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);
        /**
         * The entityIDs.
         */
        public static final JsonFieldDefinition<JsonArray> ENTITY_IDS =
                JsonFactory.newJsonArrayFieldDefinition("entityIds",
                        FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    }

}
