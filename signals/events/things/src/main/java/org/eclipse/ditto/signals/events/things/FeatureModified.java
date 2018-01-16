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
 * This event is emitted after a {@link Feature} was modified.
 */
@Immutable
public final class FeatureModified extends AbstractThingEvent<FeatureModified>
        implements ThingModifiedEvent<FeatureModified>, WithFeatureId {

    /**
     * Name of the "Feature Modified" event.
     */
    public static final String NAME = "featureModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Feature feature;

    private FeatureModified(final String thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.feature = checkNotNull(feature, "Feature");
    }

    /**
     * Constructs a new {@code FeatureModified} object.
     *
     * @param thingId the ID of the Thing on which the Feature was modified.
     * @param feature the modified {@link Feature}.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureModified of(final String thingId,
            final Feature feature,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, feature, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeatureModified} object.
     *
     * @param thingId the ID of the Thing on which the Feature was modified.
     * @param feature the modified {@link Feature}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeatureModified of(final String thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeatureModified(thingId, feature, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureModified} which was created from the given JSON string.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureModified' format.
     */
    public static FeatureModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureModified} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureModified' format.
     */
    public static FeatureModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureModified>(TYPE, jsonObject).deserialize((revision, timestamp) -> {

            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);
            final JsonObject extractedFeatureJson = jsonObject.getValueOrThrow(JSON_FEATURE);
            final Feature extractedFeature = ThingsModelFactory.newFeatureBuilder(extractedFeatureJson)
                    .useId(extractedFeatureId)
                    .build();

            return of(extractedThingId, extractedFeature, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the modified {@link Feature}.
     *
     * @return the modified Feature.
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
    public FeatureModified setRevision(final long revision) {
        return of(getThingId(), feature, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeatureModified setDittoHeaders(final DittoHeaders dittoHeaders) {
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
        final FeatureModified that = (FeatureModified) o;
        return that.canEqual(this) && Objects.equals(feature, that.feature) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof FeatureModified);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", feature=" + feature + "]";
    }

}
