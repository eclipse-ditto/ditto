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

import org.eclipse.ditto.json.JsonArray;
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
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Feature's {@link FeatureDefinition} was modified.
 */
@Immutable
public final class FeatureDefinitionModified extends AbstractThingEvent<FeatureDefinitionModified>
        implements ThingModifiedEvent<FeatureDefinitionModified>, WithFeatureId {

    /**
     * Name of the "Feature Definition Modified" event.
     */
    public static final String NAME = "featureDefinitionModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String featureId;
    private final FeatureDefinition definition;

    private FeatureDefinitionModified(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.definition = checkNotNull(definition, "Feature Definition");
    }

    /**
     * Constructs a new {@code FeatureDefinitionModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Definition was modified.
     * @param featureId the ID of the Feature whose Definition was modified.
     * @param definition the modified {@link FeatureDefinition}.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureDefinitionModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static FeatureDefinitionModified of(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, featureId, definition, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code FeatureDefinitionModified} object.
     *
     * @param thingId the ID of the Thing whose Feature's Definition was modified.
     * @param featureId the ID of the Feature whose Definition was modified.
     * @param definition the modified {@link FeatureDefinition}.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the FeatureDefinitionModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static FeatureDefinitionModified of(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new FeatureDefinitionModified(thingId, featureId, definition, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDefinitionModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new FeatureDefinitionModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDefinitionModified} which was created from the given JSON string.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'FeatureDefinitionModified' format.
     */
    public static FeatureDefinitionModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code FeatureDefinitionModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new FeatureDefinitionModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code FeatureDefinitionModified} which was created from the given JSON object.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'FeatureDefinitionModified' format.
     */
    public static FeatureDefinitionModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<FeatureDefinitionModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
                    final String extractedFeatureId = jsonObject.getValueOrThrow(JsonFields.FEATURE_ID);
                    final JsonArray definitionJsonArray = jsonObject.getValueOrThrow(JSON_DEFINITION);

                    final FeatureDefinition extractedDefinition =
                            ThingsModelFactory.newFeatureDefinition(definitionJsonArray);

                    return of(extractedThingId, extractedFeatureId, extractedDefinition, revision, timestamp,
                            dittoHeaders);
                });
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Returns the modified {@link FeatureDefinition}.
     *
     * @return the modified Definition.
     */
    public FeatureDefinition getDefinition() {
        return definition;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(definition.toJson());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/features/" + featureId + "/definition");
    }

    @Override
    public FeatureDefinitionModified setRevision(final long revision) {
        return of(getThingId(), featureId, definition, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public FeatureDefinitionModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), featureId, definition, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DEFINITION, definition.toJson(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), featureId, definition);
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
        final FeatureDefinitionModified that = (FeatureDefinitionModified) o;
        return that.canEqual(this) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(definition, that.definition) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof FeatureDefinitionModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", featureId=" + featureId +
                ", definition=" + definition +
                "]";
    }

}
