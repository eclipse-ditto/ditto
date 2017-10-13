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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after an {@code Attribute} was deleted.
 */
@Immutable
public final class AttributeDeleted extends AbstractThingEvent<AttributeDeleted>
        implements ThingModifiedEvent<AttributeDeleted> {

    /**
     * Name of the "Thing Attribute Deleted" event.
     */
    public static final String NAME = "attributeDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFactory.newStringFieldDefinition("attribute", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final JsonPointer attributePointer;

    private AttributeDeleted(final String thingId,
            final JsonPointer attributePointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.attributePointer = Objects.requireNonNull(attributePointer, "The attributes key must not be null!");
    }

    /**
     * Constructs a new {@code AttributeDeleted} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param attributePointer the key of the attribute with which this event is associated.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributeDeleted created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AttributeDeleted of(final String thingId,
            final JsonPointer attributePointer,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, attributePointer, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code AttributeDeleted} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param attributePointer the key of the attribute with which this event is associated.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AttributeDeleted created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static AttributeDeleted of(final String thingId,
            final JsonPointer attributePointer,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new AttributeDeleted(thingId, attributePointer, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code AttributeDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new AttributeDeleted instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributeDeleted} which was deleted from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AttributeDeleted' format.
     */
    public static AttributeDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AttributeDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new AttributeDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AttributeDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'AttributeDeleted' format.
     */
    public static AttributeDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<AttributeDeleted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final String pointerString = jsonObject.getValueOrThrow(JSON_ATTRIBUTE);
            final JsonPointer extractedAttributePointer = JsonFactory.newPointer(pointerString);

            return of(extractedThingId, extractedAttributePointer, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the key of the deleted attribute.
     *
     * @return the key of the deleted attribute.
     */
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/attributes" + attributePointer;
        return JsonPointer.of(path);
    }

    @Override
    public AttributeDeleted setRevision(final long revision) {
        return of(getThingId(), attributePointer, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public AttributeDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), attributePointer, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(attributePointer);
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
        final AttributeDeleted that = (AttributeDeleted) o;
        return that.canEqual(this) && Objects.equals(attributePointer, that.attributePointer) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AttributeDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", attributePointer=" + attributePointer + "]";
    }

}
