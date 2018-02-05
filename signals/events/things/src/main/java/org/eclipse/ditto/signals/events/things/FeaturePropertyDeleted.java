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

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Property of a Feature's {@link org.eclipse.ditto.model.things.FeatureProperties} was
 * deleted.
 */
@Immutable
public final class FeaturePropertyDeleted extends AbstractThingEvent<FeaturePropertyDeleted> implements
        ThingModifiedEvent<FeaturePropertyDeleted>, WithFeatureId {

    /**
     * Name of the "Feature Property Deleted" event.
     */
    public static final String NAME = "featurePropertyDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_PROPERTY =
            JsonFactory.newStringFieldDefinition("property", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String featureId;
    private final JsonPointer propertyPointer;

    private FeaturePropertyDeleted(final String thingId,
            final String featureId,
            final JsonPointer propertyPointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.featureId = requireNonNull(featureId, "The Feature ID must not be null!");
        this.propertyPointer = Objects.requireNonNull(propertyPointer, "The Property JSON Pointer must not be null!");
    }

    /**
     * Constructs a new {@code PropertyDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's Property was deleted.
     * @param featureId the ID of the Feature whose Property was deleted.
     * @param propertyJsonPointer the JSON pointer of the deleted Property key.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturePropertyDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeaturePropertyDeleted of(final String thingId,
            final String featureId,
            final JsonPointer propertyJsonPointer,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, featureId, propertyJsonPointer, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PropertyDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's Property was deleted.
     * @param featureId the ID of the Feature whose Property was deleted.
     * @param propertyJsonPointer the JSON pointer of the deleted Property key.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturePropertyDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeaturePropertyDeleted of(final String thingId,
            final String featureId,
            final JsonPointer propertyJsonPointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeaturePropertyDeleted(thingId, featureId, propertyJsonPointer, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PropertyDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new PropertyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PropertyDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PropertyDeleted' format.
     */
    public static FeaturePropertyDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturePropertyDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeaturePropertyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturePropertyDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeaturePropertyDeleted' format.
     */
    public static FeaturePropertyDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeaturePropertyDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);
                    final JsonPointer extractedPointer =
                            JsonFactory.newPointer(jsonObject.getValueOrThrow(JSON_PROPERTY));

                    return of(extractedThingId, extractedFeatureId, extractedPointer, revision, timestamp,
                            dittoHeaders);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the deleted Property.
     *
     * @return the JSON pointer of the deleted Property.
     */
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/properties" + propertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    public FeaturePropertyDeleted setRevision(final long revision) {
        return of(getThingId(), featureId, propertyPointer, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeaturePropertyDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), featureId, propertyPointer, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTY, propertyPointer.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
        result = prime * result + Objects.hashCode(propertyPointer);
        return result;
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
        final FeaturePropertyDeleted that = (FeaturePropertyDeleted) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && Objects
                .equals(propertyPointer, that.propertyPointer) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeaturePropertyDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId
                + ", propertyPointer=" + propertyPointer + "]";
    }

}
