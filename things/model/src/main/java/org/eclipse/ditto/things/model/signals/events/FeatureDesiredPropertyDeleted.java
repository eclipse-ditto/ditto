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
import org.eclipse.ditto.things.model.ThingId;

/**
 * This event is emitted after a desired property of a Feature's desired properties was deleted.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableEvent(name = FeatureDesiredPropertyDeleted.NAME, typePrefix = ThingEvent.TYPE_PREFIX)
public final class FeatureDesiredPropertyDeleted extends AbstractThingEvent<FeatureDesiredPropertyDeleted> implements
        ThingModifiedEvent<FeatureDesiredPropertyDeleted>, WithFeatureId {

    /**
     * Name of the "Feature Desired Property Deleted" event.
     */
    public static final String NAME = "featureDesiredPropertyDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFactory.newStringFieldDefinition("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String featureId;
    private final JsonPointer desiredPropertyPointer;

    private FeatureDesiredPropertyDeleted(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders, metadata);
        this.featureId = checkNotNull(featureId == null || featureId.toString().isEmpty() ? null : featureId.toString(),
                "featureId");
        this.desiredPropertyPointer = checkNotNull(desiredPropertyPointer, "desiredPropertyPointer");
    }

    /**
     * Constructs a new {@code FeatureDesiredPropertyDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's desired property was deleted.
     * @param featureId the ID of the Feature whose desired property was deleted.
     * @param desiredPropertyJsonPointer the JSON pointer of the deleted desired property key.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the FeatureDesiredPropertyDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static FeatureDesiredPropertyDeleted of(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyJsonPointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new FeatureDesiredPropertyDeleted(thingId, featureId, desiredPropertyJsonPointer, revision, timestamp,
                dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertyDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDesiredPropertyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertyDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDesiredPropertyDeleted' format.
     */
    public static FeatureDesiredPropertyDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDesiredPropertyDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureDesiredPropertyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDesiredPropertyDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureDesiredPropertyDeleted' format.
     */
    public static FeatureDesiredPropertyDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureDesiredPropertyDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.THING_ID);
                    final ThingId thingId = ThingId.of(extractedThingId);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(ThingEvent.JsonFields.FEATURE_ID);
                    final JsonPointer extractedPointer =
                            JsonFactory.newPointer(jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY));

                    return of(thingId, extractedFeatureId, extractedPointer, revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the JSON pointer of the deleted desired property.
     *
     * @return the JSON pointer of the deleted desired property.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties" + desiredPropertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    public FeatureDesiredPropertyDeleted setRevision(final long revision) {
        return of(getEntityId(), featureId, desiredPropertyPointer, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public FeatureDesiredPropertyDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), featureId, desiredPropertyPointer, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
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
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(featureId);
        result = prime * result + Objects.hashCode(desiredPropertyPointer);
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
        final FeatureDesiredPropertyDeleted that = (FeatureDesiredPropertyDeleted) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && Objects
                .equals(desiredPropertyPointer, that.desiredPropertyPointer) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeatureDesiredPropertyDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer + "]";
    }

}
