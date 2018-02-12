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

import static java.util.Objects.requireNonNull;

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
 * This event is emitted after a {@link org.eclipse.ditto.model.things.Feature} was deleted.
 */
@Immutable
public final class FeatureDeleted extends AbstractThingEvent<FeatureDeleted> implements
        ThingModifiedEvent<FeatureDeleted>, WithFeatureId {

    /**
     * Name of the "Feature Deleted" event.
     */
    public static final String NAME = "featureDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String featureId;

    private FeatureDeleted(final String thingId, final String featureId, final long revision,
            final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.featureId = requireNonNull(featureId, "The Feature ID must not be null!");
    }

    /**
     * Constructs a new {@code FeatureDeleted} object.
     *
     * @param thingId the ID of the Thing on which the Feature was deleted.
     * @param featureId the ID of the {@code Feature} which was deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureDeleted of(final String thingId, final String featureId, final long revision,
            final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeatureDeleted} object.
     *
     * @param thingId the ID of the Thing on which the Feature was deleted.
     * @param featureId the ID of the {@code Feature} which was deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureDeleted of(final String thingId, final String featureId, final long revision,
            final Instant timestamp, final DittoHeaders dittoHeaders) {
        return new FeatureDeleted(thingId, featureId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDeleted' format.
     */
    public static FeatureDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureDeleted' format.
     */
    public static FeatureDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureDeleted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
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
        final String path = "/features/" + getFeatureId();
        return JsonPointer.of(path);
    }

    @Override
    public FeatureDeleted setRevision(final long revision) {
        return of(getThingId(), featureId, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeatureDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
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
        final FeatureDeleted that = (FeatureDeleted) o;
        return that.canEqual(this) && Objects.equals(featureId, that.featureId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof FeatureDeleted);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", featureId=" + featureId + "]";
    }

}
