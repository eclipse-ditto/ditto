/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.persistentactors;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * An "empty" event which can be persisted to the backing event journal which does not contain any change "instruction"
 * to alter the managed entity's state.
 */
@Immutable
@JsonParsableEvent(name = EmptyEvent.NAME, typePrefix = EmptyEvent.TYPE_PREFIX)
public final class EmptyEvent implements Event<EmptyEvent> {

    /**
     * Known effect of the "empty event" which shall keep an persistence actor always alive.
     */
    public static final JsonValue EFFECT_ALWAYS_ALIVE = JsonValue.of("alwaysAlive");

    static final String TYPE_PREFIX = "persistence-actor-internal:";

    static final String NAME = "empty-event";

    static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_ENTITY_ID =
            JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonValue> JSON_EFFECT =
            JsonFactory.newJsonValueFieldDefinition("effect", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final EntityId entityId;
    private final JsonValue effect;
    private final long revision;
    private final DittoHeaders dittoHeaders;

    public EmptyEvent(final EntityId entityId, final JsonValue effect, final long revision,
            final DittoHeaders dittoHeaders) {
        this.entityId = entityId;
        this.revision = revision;
        this.effect = effect;
        this.dittoHeaders = dittoHeaders;
    }

    /**
     * Creates a {@code EmptyEvent} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    @SuppressWarnings("unused") // used via reflection
    public static EmptyEvent fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<EmptyEvent>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final EntityId readEntityId = DefaultEntityId.of(jsonObject.getValueOrThrow(JSON_ENTITY_ID));
                    final JsonValue readEffect = jsonObject.getValueOrThrow(JSON_EFFECT);
                    return new EmptyEvent(readEntityId, readEffect, revision, dittoHeaders);
                });
    }

    /**
     * Returns the effect of the empty event - might also be a Json {@code null} if no effect is provided.
     *
     * @return the effect of the empty event.
     */
    public JsonValue getEffect() {
        return effect;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public EmptyEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new EmptyEvent(entityId, effect, revision, dittoHeaders);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, getType())
                .set(JSON_ENTITY_ID, entityId.toString())
                .set(JSON_EFFECT, effect);
        return jsonObjectBuilder.build();
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return "empty";
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public EmptyEvent setRevision(final long revision) {
        return new EmptyEvent(entityId, effect, revision, dittoHeaders);
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.empty();
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                ", effect=" + effect +
                ", revision=" + revision +
                ", dittoHeaders=" + dittoHeaders +
                "]";
    }
}
