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
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * This event is emitted after a {@link org.eclipse.ditto.things.model.Feature} was modified.
 */
@Immutable
@JsonParsableEvent(name = FeatureModified.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
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
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final Feature feature;

    private FeatureModified(final ThingId thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.feature = checkNotNull(feature, "Feature");
    }

    /**
     * Constructs a new {@code FeatureModified} object.
     *
     * @param thingId the ID of the Thing on which the Feature was modified.
     * @param feature the modified {@link org.eclipse.ditto.things.model.Feature}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeatureModified created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     * @since 1.3.0
     */
    public static FeatureModified of(final ThingId thingId,
            final Feature feature,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeatureModified(thingId, feature, revision, timestamp, dittoHeaders, metadata);
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
        return new EventJsonDeserializer<FeatureModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {

                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);
                    final JsonObject extractedFeatureJson = jsonObject.getValueOrThrow(JSON_FEATURE);
                    final Feature extractedFeature = ThingsModelFactory.newFeatureBuilder(extractedFeatureJson)
                            .useId(extractedFeatureId)
                            .build();

                    return of(thingId, extractedFeature, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the modified {@link org.eclipse.ditto.things.model.Feature}.
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
    public FeatureModified setEntity(final JsonValue entity) {
        return of(getEntityId(),
                ThingsModelFactory.newFeatureBuilder(entity.asObject()).useId(getFeatureId()).build(),
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/features/" + getFeatureId());
    }

    @Override
    public FeatureModified setRevision(final long revision) {
        return of(getEntityId(), feature, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public FeatureModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), feature, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.FEATURE_ID, getFeatureId(), predicate);
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
