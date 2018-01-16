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
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Feature's {@link FeatureProperties} were modified.
 */
@Immutable
public final class FeaturePropertiesModified extends AbstractThingEvent<FeaturePropertiesModified> implements
        ThingModifiedEvent<FeaturePropertiesModified>, WithFeatureId {

    /**
     * Name of the "Feature Properties Modified" event.
     */
    public static final String NAME = "featurePropertiesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String featureId;
    private final FeatureProperties properties;

    private FeaturePropertiesModified(final String thingId,
            final String featureId,
            final FeatureProperties properties,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.properties = checkNotNull(properties, "Feature Properties");
    }

    /**
     * Constructs a new {@code PropertiesModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Properties were modified.
     * @param featureId the ID of the Feature whose Properties were modified.
     * @param properties the modified {@link FeatureProperties}.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturePropertiesModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeaturePropertiesModified of(final String thingId,
            final String featureId,
            final FeatureProperties properties,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, featureId, properties, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PropertiesModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Properties were modified.
     * @param featureId the ID of the Feature whose Properties were modified.
     * @param properties the modified {@link FeatureProperties}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeaturePropertiesModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeaturePropertiesModified of(final String thingId,
            final String featureId,
            final FeatureProperties properties,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeaturePropertiesModified(thingId, featureId, properties, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PropertiesModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new PropertiesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PropertiesModified} which was created from the given JSON string.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PropertiesModified' format.
     */
    public static FeaturePropertiesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeaturePropertiesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeaturePropertiesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeaturePropertiesModified} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeaturePropertiesModified' format.
     */
    public static FeaturePropertiesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeaturePropertiesModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);
                    final JsonObject propertiesJsonObject = jsonObject.getValueOrThrow(JSON_PROPERTIES);

                    final FeatureProperties extractedProperties = (null != propertiesJsonObject)
                            ? ThingsModelFactory.newFeatureProperties(propertiesJsonObject)
                            : ThingsModelFactory.nullFeatureProperties();

                    return of(extractedThingId, extractedFeatureId, extractedProperties, revision, timestamp,
                            dittoHeaders);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the modified {@link FeatureProperties}.
     *
     * @return the modified Properties.
     */
    public FeatureProperties getProperties() {
        return properties;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(properties.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/features/" + featureId + "/properties");
    }

    @Override
    public FeaturePropertiesModified setRevision(final long revision) {
        return of(getThingId(), featureId, properties, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeaturePropertiesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), featureId, properties, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_PROPERTIES, properties, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), featureId, properties);
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
        final FeaturePropertiesModified that = (FeaturePropertiesModified) o;
        return that.canEqual(this) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(properties, that.properties) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof FeaturePropertiesModified);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", featureId=" + featureId +
                ", properties=" + properties +
                "]";
    }

}
