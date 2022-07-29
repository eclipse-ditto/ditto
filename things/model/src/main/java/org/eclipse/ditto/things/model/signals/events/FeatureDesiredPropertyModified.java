/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
 * This event is emitted after a desired property of a Feature's desired properties was modified.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableEvent(name = FeatureDesiredPropertyModified.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class FeatureDesiredPropertyModified extends AbstractThingEvent<FeatureDesiredPropertyModified> implements
        ThingModifiedEvent<FeatureDesiredPropertyModified>, WithFeatureId {

    /**
     * Name of the "Feature Desired Property Modified" event.
     */
    public static final String NAME = "featureDesiredPropertyModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFactory.newStringFieldDefinition("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_DESIRED_VALUE =
            JsonFactory.newJsonValueFieldDefinition("desiredValue", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String featureId;
    private final JsonPointer desiredPropertyPointer;
    private final JsonValue desiredPropertyValue;

    private FeatureDesiredPropertyModified(final ThingId thingId,
            final String featureId,
            final JsonPointer desiredPropertyPointer,
            final JsonValue desiredPropertyValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.featureId = checkNotNull(featureId, "featureId");
        this.desiredPropertyPointer = checkNotNull(desiredPropertyPointer, "desiredPropertyPointer");
        this.desiredPropertyValue = checkNotNull(desiredPropertyValue, "desiredPropertyValue");
    }

    /**
     * Constructs a new {@code FeatureDesiredPropertyModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's desired property was modified.
     * @param featureId the ID of the Feature whose desired property was modified.
     * @param desiredPropertyJsonPointer the JSON pointer of the modified desired property key.
     * @param desiredPropertyValue the value of the modified desired property.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeatureDesiredPropertyModified created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static FeatureDesiredPropertyModified of(final ThingId thingId,
            final String featureId,
            final JsonPointer desiredPropertyJsonPointer,
            final JsonValue desiredPropertyValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeatureDesiredPropertyModified(thingId, featureId, desiredPropertyJsonPointer, desiredPropertyValue,
                revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertyModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDesiredPropertyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertyModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDesiredPropertyModified' format.
     */
    public static FeatureDesiredPropertyModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertyModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureDesiredPropertyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertyModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureDesiredPropertyModified' format.
     */
    public static FeatureDesiredPropertyModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureDesiredPropertyModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);
                    final String pointerString = jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY);
                    final JsonPointer extractedPointer = JsonFactory.newPointer(pointerString);
                    final JsonValue extractedValue = jsonObject.getValueOrThrow(JSON_DESIRED_VALUE);

                    return of(thingId, extractedFeatureId, extractedPointer, extractedValue, revision,
                            timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the desired property to modify.
     *
     * @return the JSON pointer of the desired property to modify.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    /**
     * Returns the value of the desired property to modify.
     *
     * @return the value of the desired property to modify.
     */
    public JsonValue getDesiredPropertyValue() {
        return desiredPropertyValue;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(desiredPropertyValue);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties" + desiredPropertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    public FeatureDesiredPropertyModified setRevision(final long revision) {
        return of(getEntityId(), featureId, desiredPropertyPointer, desiredPropertyValue, revision,
                getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public FeatureDesiredPropertyModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, desiredPropertyPointer, desiredPropertyValue, getRevision(),
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
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), predicate);
        jsonObjectBuilder.set(JSON_DESIRED_VALUE, desiredPropertyValue, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
        result = prime * result + Objects.hashCode(desiredPropertyPointer);
        result = prime * result + Objects.hashCode(desiredPropertyValue);
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
        final FeatureDesiredPropertyModified that = (FeatureDesiredPropertyModified) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && Objects
                .equals(desiredPropertyPointer, that.desiredPropertyPointer) &&
                Objects.equals(desiredPropertyValue, that.desiredPropertyValue)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeatureDesiredPropertyModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer + ", desiredPropertyValue=" +
                desiredPropertyValue + "]";
    }

}
