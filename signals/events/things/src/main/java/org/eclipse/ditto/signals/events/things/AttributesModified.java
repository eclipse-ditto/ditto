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
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after all {@code Attribute}s were modified at once.
 */
@Immutable
public final class AttributesModified extends AbstractThingEvent<AttributesModified>
        implements ThingModifiedEvent<AttributesModified> {

    /**
     * Name of the "Thing Attribute Modified" event.
     */
    public static final String NAME = "attributesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ATTRIBUTES =
            JsonFactory.newJsonObjectFieldDefinition("attributes", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Attributes attributesModified;

    private AttributesModified(final String thingId,
            final Attributes attributesModified,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.attributesModified =
                Objects.requireNonNull(attributesModified, "The modified attributes must not be null!");
    }

    /**
     * Constructs a new {@code AttributesModified} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param attributesModified the changes on the attributes object.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AttributesModified of(final String thingId,
            final Attributes attributesModified,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, attributesModified, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributesModified} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param attributesModified the changes on the attributes object.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributesModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static AttributesModified of(final String thingId,
            final Attributes attributesModified,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new AttributesModified(thingId, attributesModified, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code AttributesModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new AttributesModified instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributesModified} which was deleted from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AttributesModified' format.
     */
    public static AttributesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AttributesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new AttributesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributesModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'AttributesModified' format.
     */
    public static AttributesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<AttributesModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
                    final JsonObject attributesJsonObject = jsonObject.getValueOrThrow(JSON_ATTRIBUTES);
                    final Attributes extractedAttributes = ThingsModelFactory.newAttributes(attributesJsonObject);

                    return of(extractedThingId, extractedAttributes, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the JSON object which represents the attributes modified.
     *
     * @return the json object.
     */
    public Attributes getModifiedAttributes() {
        return attributesModified;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(attributesModified.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/attributes");
    }

    @Override
    public AttributesModified setRevision(final long revision) {
        return of(getThingId(), attributesModified, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public AttributesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), attributesModified, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTES, getModifiedAttributes(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(attributesModified);
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
        final AttributesModified that = (AttributesModified) o;
        return that.canEqual(this) && Objects.equals(attributesModified, that.attributesModified) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AttributesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", attributesModified=" + attributesModified +
                "]";
    }

}
