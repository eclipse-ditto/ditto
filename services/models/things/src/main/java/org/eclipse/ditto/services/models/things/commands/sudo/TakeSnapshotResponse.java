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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Actor message sent as response from PersistenceActors to the {@link TakeSnapshot} message indicating success.
 */
public final class TakeSnapshotResponse extends AbstractCommandResponse<TakeSnapshotResponse>
        implements SudoCommandResponse<TakeSnapshotResponse> {

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + TakeSnapshot.NAME;

    static final JsonFieldDefinition<Long> JSON_SNAPSHOT_REVISION =
            JsonFactory.newLongFieldDefinition("snapshotRevision", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final long snapshotRevision;

    private TakeSnapshotResponse(final HttpStatusCode statusCode, final long snapshotRevision,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.snapshotRevision = snapshotRevision;
    }

    /**
     * Creates a response to a {@link TakeSnapshot} command.
     *
     * @param snapshotRevision the revision number of the snapshot taken.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TakeSnapshotResponse of(final long snapshotRevision, final DittoHeaders dittoHeaders) {
        return new TakeSnapshotResponse(HttpStatusCode.OK, snapshotRevision, dittoHeaders);
    }

    /**
     * Creates a response to a {@code TakeSnapshotResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static TakeSnapshotResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code TakeSnapshotResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TakeSnapshotResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return of(jsonObject.getValueOrThrow(JSON_SNAPSHOT_REVISION), dittoHeaders);
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
    public TakeSnapshotResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(entity.asLong(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(snapshotRevision);
    }

    @Override
    public TakeSnapshotResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(snapshotRevision, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_SNAPSHOT_REVISION, snapshotRevision, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof TakeSnapshotResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TakeSnapshotResponse that = (TakeSnapshotResponse) o;
        return that.canEqual(this) && Objects.equals(snapshotRevision, that.snapshotRevision) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), snapshotRevision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", snapshotRevision=" + snapshotRevision + "]";
    }

}
