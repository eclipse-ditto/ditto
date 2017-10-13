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
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after the Thing ACL was modified.
 */
@Immutable
public final class AclModified extends AbstractThingEvent<AclModified> implements ThingModifiedEvent<AclModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "aclModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ACCESS_CONTROL_LIST =
            JsonFactory.newJsonObjectFieldDefinition("acl", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final AccessControlList accessControlList;

    private AclModified(final String thingId,
            final AccessControlList accessControlList,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.accessControlList =
                Objects.requireNonNull(accessControlList, "The modified Access Control List must not be null!");
    }

    /**
     * Constructs a new {@code AclModified} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param accessControlList the modified Access Control List.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AclModified created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclModified of(final String thingId,
            final AccessControlList accessControlList,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, accessControlList, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code AclModified} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param accessControlList the modified Access Control List.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the AclModified created.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static AclModified of(final String thingId,
            final AccessControlList accessControlList,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new AclModified(thingId, accessControlList, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code AclModified} from a JSON string.
     *
     * @param jsonString the JSON string of which a new AclModified instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AclModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AclModified' format.
     */
    public static AclModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AclModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new AclModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AclModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'AclModified' format.
     */
    public static AclModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<AclModified>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final JsonObject aclJsonObject = jsonObject.getValueOrThrow(JSON_ACCESS_CONTROL_LIST);
            final AccessControlList extractedAccessControlList = ThingsModelFactory.newAcl(aclJsonObject);

            return of(extractedThingId, extractedAccessControlList, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the modified Access Control List.
     *
     * @return the Access Control List.
     */
    public AccessControlList getAccessControlList() {
        return accessControlList;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(accessControlList.toJson(FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/acl");
    }

    @Override
    public AclModified setRevision(final long revision) {
        return of(getThingId(), accessControlList, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public AclModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), accessControlList, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ACCESS_CONTROL_LIST, accessControlList.toJson(schemaVersion, thePredicate),
                predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(accessControlList);
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
        final AclModified that = (AclModified) o;
        return that.canEqual(this) && Objects.equals(accessControlList, that.accessControlList) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AclModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", accessControlList=" + accessControlList + "]";
    }

}
