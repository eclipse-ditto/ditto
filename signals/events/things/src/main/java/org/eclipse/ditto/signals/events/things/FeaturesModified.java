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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Thing's {@link Features} were modified.
 */
@Immutable
public final class FeaturesModified extends AbstractThingEvent<FeaturesModified>
        implements ThingModifiedEvent<FeaturesModified> {

    /**
     * Name of the "Features Modified" event.
     */
    public static final String NAME = "featuresModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Features features;

    private FeaturesModified(final String thingId,
            final Features features,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.features = checkNotNull(features, "Features");
    }

    /**
     * Constructs a new {@code FeaturesModified} object.
     *
     * @param thingId the ID of the Thing on which the Features was modified.
     * @param features the modified {@link Features}.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturesModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeaturesModified of(final String thingId,
            final Features features,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, features, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeaturesModified} object.
     *
     * @param thingId the ID of the Thing on which the Features was modified.
     * @param features the modified {@link Features}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturesModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeaturesModified of(final String thingId,
            final Features features,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeaturesModified(thingId, features, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturesModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeaturesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturesModified} which was created from the given JSON string.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeaturesModified' format.
     */
    public static FeaturesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeaturesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturesModified} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeaturesModified' format.
     */
    public static FeaturesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeaturesModified>(TYPE, jsonObject).deserialize((revision, timestamp) -> {

            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final JsonObject featuresJsonObject = jsonObject.getValueOrThrow(JSON_FEATURES);
            final Features extractedFeatures = ThingsModelFactory.newFeatures(featuresJsonObject);

            return of(extractedThingId, extractedFeatures, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the modified {@link Features}.
     *
     * @return the modified Features.
     */
    public Features getFeatures() {
        return features;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(features.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/features");
    }

    @Override
    public FeaturesModified setRevision(final long revision) {
        return of(getThingId(), features, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeaturesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), features, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_FEATURES, features.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), features);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeaturesModified that = (FeaturesModified) o;
        return that.canEqual(this) && Objects.equals(features, that.features) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeaturesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", features=" + features + "]";
    }

}
