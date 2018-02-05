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
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link Feature} was created.
 */
@Immutable
public final class FeatureCreated extends AbstractThingEvent<FeatureCreated> implements
        ThingModifiedEvent<FeatureCreated>, WithFeatureId {

    /**
     * Name of the "Feature Created" event.
     */
    public static final String NAME = "featureCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Feature feature;

    private FeatureCreated(final String thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.feature = feature;
    }

    /**
     * Constructs a new {@code FeatureCreated} object.
     *
     * @param thingId the ID of the Thing on which the Feature was created.
     * @param feature the created {@link Feature}.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureCreated created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureCreated of(final String thingId,
            final Feature feature,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, feature, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeatureCreated} object.
     *
     * @param thingId the ID of the Thing on which the Feature was created.
     * @param feature the created {@link Feature}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureCreated created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeatureCreated of(final String thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        checkNotNull(feature, "Feature");
        return new FeatureCreated(thingId, feature, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureCreated} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureCreated' format.
     */
    public static FeatureCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureCreated' format.
     */
    public static FeatureCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureCreated>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);
            final JsonObject featureJsonObject = jsonObject.getValueOrThrow(JSON_FEATURE);

            final Feature extractedFeature = !featureJsonObject.isNull()
                    ? ThingsModelFactory.newFeatureBuilder(featureJsonObject).useId(extractedFeatureId).build()
                    : ThingsModelFactory.nullFeature(extractedFeatureId);

            return of(extractedThingId, extractedFeature, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the created {@link Feature}.
     *
     * @return the created Feature.
     */
    public Feature getFeature() {
        return feature;
    }

    @Override
    public String getFeatureId() {
        return feature.getId();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(feature.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/features/" + getFeatureId());
    }

    @Override
    public FeatureCreated setRevision(final long revision) {
        return of(getThingId(), feature, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeatureCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), feature, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.FEATURE_ID, getFeatureId(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE, feature.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), feature);
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
        final FeatureCreated that = (FeatureCreated) o;
        return that.canEqual(this) && Objects.equals(feature, that.feature) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeatureCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", feature=" + feature + "]";
    }

}
