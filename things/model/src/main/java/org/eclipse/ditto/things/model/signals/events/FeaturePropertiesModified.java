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
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * This event is emitted after a Feature's {@link org.eclipse.ditto.things.model.FeatureProperties} were modified.
 */
@Immutable
@JsonParsableEvent(name = FeaturePropertiesModified.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
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
            JsonFactory.newJsonObjectFieldDefinition("properties", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String featureId;
    private final FeatureProperties properties;

    private FeaturePropertiesModified(final ThingId thingId,
            final String featureId,
            final FeatureProperties properties,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.properties = checkNotNull(properties, "Feature Properties");
    }

    /**
     * Constructs a new {@code PropertiesModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Properties were modified.
     * @param featureId the ID of the Feature whose Properties were modified.
     * @param properties the modified {@link org.eclipse.ditto.things.model.FeatureProperties}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeaturePropertiesModified created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     * @since 1.3.0
     */
    public static FeaturePropertiesModified of(final ThingId thingId,
            final String featureId,
            final FeatureProperties properties,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeaturePropertiesModified(thingId, featureId, properties, revision, timestamp, dittoHeaders,
                metadata);
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
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);
                    final JsonObject propertiesJsonObject = jsonObject.getValueOrThrow(JSON_PROPERTIES);

                    final FeatureProperties extractedProperties = (null != propertiesJsonObject)
                            ? ThingsModelFactory.newFeatureProperties(propertiesJsonObject)
                            : ThingsModelFactory.nullFeatureProperties();

                    return of(thingId, extractedFeatureId, extractedProperties, revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the modified {@link org.eclipse.ditto.things.model.FeatureProperties}.
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
        return of(getEntityId(), featureId, properties, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public FeaturePropertiesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, properties, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.FEATURE_ID, featureId, predicate);
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
