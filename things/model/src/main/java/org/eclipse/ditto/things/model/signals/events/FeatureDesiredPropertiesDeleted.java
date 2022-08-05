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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;

/**
 * This event is emitted after a Feature's desired properties were deleted.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableEvent(name = FeatureDesiredPropertiesDeleted.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class FeatureDesiredPropertiesDeleted extends AbstractThingEvent<FeatureDesiredPropertiesDeleted>
        implements ThingModifiedEvent<FeatureDesiredPropertiesDeleted>, WithFeatureId {

    /**
     * Name of the "Feature Desired Properties Deleted" event.
     */
    public static final String NAME = "featureDesiredPropertiesDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String featureId;

    private FeatureDesiredPropertiesDeleted(final ThingId thingId,
            final CharSequence featureId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.featureId = checkNotNull(featureId == null || featureId.toString().isEmpty() ? null : featureId.toString(),
                "featureId");
    }

    /**
     * Constructs a new {@code FeatureDesiredPropertiesDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's desired properties were deleted.
     * @param featureId the ID of the Feature whose desired properties were deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeatureDesiredPropertiesDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static FeatureDesiredPropertiesDeleted of(final ThingId thingId,
            final CharSequence featureId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeatureDesiredPropertiesDeleted(thingId, featureId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertiesDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDesiredPropertiesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertiesDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDesiredPropertiesDeleted' format.
     */
    public static FeatureDesiredPropertiesDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertiesDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureDesiredPropertiesDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertiesDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureDesiredPropertiesDeleted' format.
     */
    public static FeatureDesiredPropertiesDeleted fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<FeatureDesiredPropertiesDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);

                    return of(thingId, extractedFeatureId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties";
        return JsonPointer.of(path);
    }

    @Override
    public FeatureDesiredPropertiesDeleted setRevision(final long revision) {
        return of(getEntityId(), featureId, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public FeatureDesiredPropertiesDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public Command.Category getCommandCategory() {
        return Command.Category.DELETE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingEvent.JsonFields.FEATURE_ID, featureId, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
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
        final FeatureDesiredPropertiesDeleted that = (FeatureDesiredPropertiesDeleted) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof FeatureDesiredPropertiesDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId + "]";
    }

}
