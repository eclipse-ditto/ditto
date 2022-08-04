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

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
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
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * This event is emitted after a {@link org.eclipse.ditto.things.model.Thing} was modified.
 */
@Immutable
@JsonParsableEvent(name = ThingModified.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class ThingModified extends AbstractThingEvent<ThingModified>
        implements ThingModifiedEvent<ThingModified> {

    /**
     * Name of the "Thing Modified" event.
     */
    public static final String NAME = "thingModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Thing thing;

    private ThingModified(final Thing thing,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, requireNonNull(thing, "The Thing must not be null!").getEntityId().orElse(null), revision,
                timestamp,
                dittoHeaders, metadata);
        this.thing = thing;
    }

    /**
     * Constructs a new {@code ThingModified} object.
     *
     * @param thing the modified {@link org.eclipse.ditto.things.model.Thing}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the ThingModified created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     * @since 1.3.0
     */
    public static ThingModified of(final Thing thing,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ThingModified(thing, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ThingModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new ThingModified instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ThingModified} which was deleted from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ThingModified' format.
     */
    public static ThingModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ThingModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ThingModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ThingModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ThingModified' format.
     */
    public static ThingModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ThingModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final JsonObject thingJsonObject =
                            jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING); // THING was in V1 and V2
                    final Thing extractedModifiedThing = ThingsModelFactory.newThing(thingJsonObject);

                    return of(extractedModifiedThing, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the created {@link org.eclipse.ditto.things.model.Thing}.
     *
     * @return the created Thing.
     */
    public Thing getThing() {
        return thing;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(thing.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ThingModified setRevision(final long revision) {
        return of(thing, revision, getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public ThingModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, getRevision(), getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.THING, thing.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(thing);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final ThingModified that = (ThingModified) o;
        return that.canEqual(this) && Objects.equals(thing, that.thing) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ThingModified);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thing=" + thing + "]";
    }

}
