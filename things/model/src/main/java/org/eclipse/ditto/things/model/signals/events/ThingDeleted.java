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
package org.eclipse.ditto.things.model.signals.events;

import java.time.Instant;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * This event is emitted after a {@link org.eclipse.ditto.things.model.Thing} was deleted.
 */
@Immutable
@JsonParsableEvent(name = ThingDeleted.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class ThingDeleted extends AbstractThingEvent<ThingDeleted> implements ThingModifiedEvent<ThingDeleted> {

    /**
     * Name of the "Thing Deleted" event.
     */
    public static final String NAME = "thingDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private ThingDeleted(final ThingId thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Constructs a new {@code ThingDeleted} object.
     *
     * @param thingId the ID of the {@link org.eclipse.ditto.things.model.Thing} that was deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the ThingDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     * @since 1.3.0
     */
    public static ThingDeleted of(final ThingId thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ThingDeleted(thingId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ThingDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new ThingDeleted instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ThingDeleted} which was deleted from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ThingDeleted' format.
     */
    public static ThingDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ThingDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ThingDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ThingDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ThingDeleted' format.
     */
    public static ThingDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);

                    return of(thingId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ThingDeleted setRevision(final long revision) {
        return of(getEntityId(), revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public ThingDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public ThingDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.DELETE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // nothing to add
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ThingDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }



}
