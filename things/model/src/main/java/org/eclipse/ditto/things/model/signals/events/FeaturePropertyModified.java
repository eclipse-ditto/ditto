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
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * This event is emitted after a Property of a Feature's {@link org.eclipse.ditto.things.model.FeatureProperties} was
 * modified.
 */
@Immutable
@JsonParsableEvent(name = FeaturePropertyModified.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class FeaturePropertyModified extends AbstractThingEvent<FeaturePropertyModified> implements
        ThingModifiedEvent<FeaturePropertyModified>, WithFeatureId {

    /**
     * Name of the "Feature Property Modified" event.
     */
    public static final String NAME = "featurePropertyModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_PROPERTY =
            JsonFactory.newStringFieldDefinition("property", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_VALUE =
            JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String featureId;
    private final JsonPointer propertyPointer;
    private final JsonValue propertyValue;

    private FeaturePropertyModified(final ThingId thingId,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.featureId = requireNonNull(featureId, "The Feature ID must not be null!");
        this.propertyPointer = Objects.requireNonNull(propertyPointer, "The Property JSON Pointer must not be null!");
        this.propertyValue = Objects.requireNonNull(propertyValue, "The Property Value must not be null!");
    }

    /**
     * Constructs a new {@code FeaturePropertyModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Property was modified.
     * @param featureId the ID of the Feature whose Property was modified.
     * @param propertyJsonPointer the JSON pointer of the modified Property key.
     * @param propertyValue the value of the modified Property.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeaturePropertyModified created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     * @since 1.3.0
     */
    public static FeaturePropertyModified of(final ThingId thingId,
            final String featureId,
            final JsonPointer propertyJsonPointer,
            final JsonValue propertyValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeaturePropertyModified(thingId, featureId, propertyJsonPointer, propertyValue, revision, timestamp,
                dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code FeaturePropertyModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeaturePropertyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturePropertyModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeaturePropertyModified' format.
     */
    public static FeaturePropertyModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturePropertyModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeaturePropertyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturePropertyModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeaturePropertyModified' format.
     */
    public static FeaturePropertyModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeaturePropertyModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);
                    final String pointerString = jsonObject.getValueOrThrow(JSON_PROPERTY);
                    final JsonPointer extractedPointer = JsonFactory.newPointer(pointerString);
                    final JsonValue extractedValue = jsonObject.getValueOrThrow(JSON_VALUE);

                    return of(thingId, extractedFeatureId, extractedPointer, extractedValue, revision,
                            timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the Property to modify.
     *
     * @return the JSON pointer of the Property to modify.
     */
    public JsonPointer getPropertyPointer() {
        return propertyPointer;
    }

    /**
     * Returns the value of the Property to modify.
     *
     * @return the value of the Property to modify.
     */
    public JsonValue getPropertyValue() {
        return propertyValue;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(propertyValue);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/properties" + propertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    public FeaturePropertyModified setRevision(final long revision) {
        return of(getEntityId(), featureId, propertyPointer, propertyValue, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public FeaturePropertyModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, propertyPointer, propertyValue, getRevision(),
                getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTY, propertyPointer.toString(), predicate);
        jsonObjectBuilder.set(JSON_VALUE, propertyValue, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
        result = prime * result + Objects.hashCode(propertyPointer);
        result = prime * result + Objects.hashCode(propertyValue);
        return result;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeaturePropertyModified that = (FeaturePropertyModified) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && Objects
                .equals(propertyPointer, that.propertyPointer) && Objects.equals(propertyValue, that.propertyValue)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeaturePropertyModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId
                + ", propertyPointer=" + propertyPointer + ", propertyValue=" + propertyValue + "]";
    }

}
