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
package org.eclipse.ditto.signals.commands.things.modify;

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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Actor message sent in response to the {@link TagThing} command indicating success.
 */
@Immutable
public final class TagThingResponse extends AbstractCommandResponse<TagThingResponse> implements
        ThingModifyCommandResponse<TagThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + TagThing.NAME;

    /**
     * Json field for the revision of the snapshot created by the {@code TagThing} command.
     */
    static final JsonFieldDefinition<Long> JSON_SNAPSHOT_REVISION =
            JsonFactory.newLongFieldDefinition("snapshotRevision", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final long snapshotRevision;

    private TagThingResponse(final String thingId, final long snapshotRevision, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.thingId = thingId;
        this.snapshotRevision = snapshotRevision;
    }

    /**
     * Creates a response to a {@link TagThing} command.
     *
     * @param thingId ID of the tagged Thing.
     * @param snapshotRevision the revision number of the snapshot taken.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TagThingResponse of(final String thingId, final long snapshotRevision,
            final DittoHeaders dittoHeaders) {
        return new TagThingResponse(thingId, snapshotRevision, dittoHeaders);
    }

    /**
     * Creates a response to a {@link TagThing} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static TagThingResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code TagThingResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TagThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String thingId = jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
        final long revision = jsonObject.getValueOrThrow(JSON_SNAPSHOT_REVISION);
        return of(thingId, revision, dittoHeaders);
    }

    /**
     * Returns the id of the snapshot taken.
     *
     * @return the snapshot id
     */
    public long getSnapshotRevision() {
        return snapshotRevision;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public TagThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, snapshotRevision, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_SNAPSHOT_REVISION, snapshotRevision, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof TagThingResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TagThingResponse that = (TagThingResponse) o;
        return that.canEqual(this) && super.equals(o) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(snapshotRevision, that.snapshotRevision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, snapshotRevision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", thingId=" + thingId +
                ", snapshotRevision=" + snapshotRevision + "]";
    }
}
