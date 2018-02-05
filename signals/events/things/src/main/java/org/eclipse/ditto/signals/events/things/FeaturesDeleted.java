/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after {@link org.eclipse.ditto.model.things.Features} were deleted.
 */
@Immutable
public final class FeaturesDeleted extends AbstractThingEvent<FeaturesDeleted>
        implements ThingModifiedEvent<FeaturesDeleted> {

    /**
     * Name of the "Features Deleted" event.
     */
    public static final String NAME = "featuresDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private FeaturesDeleted(final String thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeaturesDeleted} object.
     *
     * @param thingId the ID of the Thing on which the Features were deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturesDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeaturesDeleted of(final String thingId, final long revision, final DittoHeaders dittoHeaders) {
        return of(thingId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeaturesDeleted} object.
     *
     * @param thingId the ID of the Thing on which the Features were deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturesDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeaturesDeleted of(final String thingId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeaturesDeleted(thingId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturesDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeaturesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturesDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeaturesDeleted' format.
     */
    public static FeaturesDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturesDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeaturesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturesDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeaturesDeleted' format.
     */
    public static FeaturesDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeaturesDeleted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);

            return of(extractedThingId, revision, timestamp, dittoHeaders);
        });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    public FeaturesDeleted setRevision(final long revision) {
        return of(getThingId(), revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeaturesDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // nothing to add
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
