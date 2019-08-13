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
package org.eclipse.ditto.signals.events.things;

import java.time.Instant;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Thing's {@link org.eclipse.ditto.model.things.Attributes} were deleted.
 */
@Immutable
@JsonParsableEvent(name = AttributesDeleted.NAME, typePrefix= AttributesDeleted.TYPE_PREFIX)
public final class AttributesDeleted extends AbstractThingEvent<AttributesDeleted>
        implements ThingModifiedEvent<AttributesDeleted> {

    /**
     * Name of the "Thing Attributes Deleted" event.
     */
    public static final String NAME = "attributesDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private AttributesDeleted(final ThingId thingId, final long revision, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        super(TYPE, thingId, revision, timestamp, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributesDeleted} object.
     *
     * @param thingId the ID of the Thing whose Attributes were deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, long, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static AttributesDeleted of(final String thingId, final long revision, final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), revision, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributesDeleted} object.
     *
     * @param thingId the ID of the Thing whose Attributes were deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AttributesDeleted of(final ThingId thingId, final long revision, final DittoHeaders dittoHeaders) {
        return of(thingId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributesDeleted} object.
     *
     * @param thingId the ID of the Thing whose Attributes were deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, long, java.time.Instant, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static AttributesDeleted of(final String thingId, final long revision, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), revision, timestamp, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributesDeleted} object.
     *
     * @param thingId the ID of the Thing whose Attributes were deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AttributesDeleted of(final ThingId thingId, final long revision, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        return new AttributesDeleted(thingId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code AttributesDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new AttributesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributesDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AttributesDeleted' format.
     */
    public static AttributesDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AttributesDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new AttributesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributesDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'AttributesDeleted' format.
     */
    public static AttributesDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<AttributesDeleted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            return of(thingId, revision, dittoHeaders);
        });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/attributes");
    }

    @Override
    public AttributesDeleted setRevision(final long revision) {
        return of(getThingEntityId(), revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public AttributesDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingEntityId(), getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        // nothing to add
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
