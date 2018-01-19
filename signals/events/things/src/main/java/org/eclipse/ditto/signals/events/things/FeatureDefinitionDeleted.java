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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Feature's {@link org.eclipse.ditto.model.things.FeatureDefinition} was deleted.
 */
@Immutable
public final class FeatureDefinitionDeleted extends AbstractThingEvent<FeatureDefinitionDeleted>
        implements ThingModifiedEvent<FeatureDefinitionDeleted>, WithFeatureId {

    /**
     * Name of the "Feature Definition Deleted" event.
     */
    public static final String NAME = "featureDefinitionDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String featureId;

    private FeatureDefinitionDeleted(final String thingId,
            final String featureId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.featureId = checkNotNull(featureId, "Feature ID");
    }

    /**
     * Constructs a new {@code FeatureDefinitionDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's Definition was deleted.
     * @param featureId the ID of the Feature whose Definition wsa deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureFeatureDefinitionDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureDefinitionDeleted of(final String thingId,
            final String featureId,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, featureId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeatureDefinitionDeleted} object.
     *
     * @param thingId the ID of the Thing whose Feature's Definition was deleted.
     * @param featureId the ID of the Feature whose Definition was deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureFeatureDefinitionDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureDefinitionDeleted of(final String thingId,
            final String featureId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeatureDefinitionDeleted(thingId, featureId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDefinitionDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDefinitionDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDefinitionDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDefinitionDeleted' format.
     */
    public static FeatureDefinitionDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureFeatureDefinitionDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureFeatureDefinitionDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureFeatureDefinitionDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureFeatureDefinitionDeleted' format.
     */
    public static FeatureDefinitionDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureDefinitionDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);

                    return of(extractedThingId, extractedFeatureId, revision, timestamp, dittoHeaders);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/definition";
        return JsonPointer.of(path);
    }

    @Override
    public FeatureDefinitionDeleted setRevision(final long revision) {
        return of(getThingId(), featureId, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeatureDefinitionDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), featureId, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.FEATURE_ID, featureId, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(featureId, super.hashCode());
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
        final FeatureDefinitionDeleted that = (FeatureDefinitionDeleted) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof FeatureDefinitionDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId + "]";
    }

}
