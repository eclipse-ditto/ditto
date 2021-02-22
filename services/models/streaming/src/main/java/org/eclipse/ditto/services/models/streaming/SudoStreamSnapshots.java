/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithIdButActuallyNot;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which starts a stream of snapshots.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = SudoStreamSnapshots.TYPE_PREFIX, name = SudoStreamSnapshots.NAME)
public final class SudoStreamSnapshots extends AbstractCommand<SudoStreamSnapshots>
        implements StartStreamRequest, WithIdButActuallyNot {

    static final String NAME = "SudoStreamSnapshots";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final int burst;
    private final long timeoutMillis;
    private final EntityId lowerBound;
    private final JsonArray snapshotFields;

    private SudoStreamSnapshots(final Integer burst,
            final Long timeoutMillis,
            final EntityId lowerBound,
            final JsonArray snapshotFields,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        this.burst = burst;
        this.timeoutMillis = timeoutMillis;
        this.lowerBound = lowerBound;
        this.snapshotFields = snapshotFields;
    }

    /**
     * Creates a new {@code SudoStreamSnapshots} command.
     *
     * @param burst the amount of snapshots to read in a batch.
     * @param timeoutMillis maximum time to wait for acknowledgement of each stream element.
     * @param fields selected fields of snapshots.
     * @param dittoHeaders the command headers of the request.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamSnapshots of(final Integer burst, final Long timeoutMillis, final List<String> fields,
            final DittoHeaders dittoHeaders) {

        final JsonArray snapshotFields = fields.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        return new SudoStreamSnapshots(burst, timeoutMillis, DefaultEntityId.dummy(), snapshotFields, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoStreamSnapshots} from a JSON object.
     *
     * @param jsonObject the JSON representation of the command.
     * @param dittoHeaders the optional command headers of the request.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SudoStreamSnapshots fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final int burst = jsonObject.getValueOrThrow(JsonFields.JSON_BURST);
        final long timeoutMillis = jsonObject.getValueOrThrow(JsonFields.JSON_TIMEOUT_MILLIS);
        final EntityId lowerBound = jsonObject.getValue(JsonFields.JSON_LOWER_BOUND)
                .map(DefaultEntityId::of)
                .orElseGet(DefaultEntityId::dummy);
        final JsonArray snapshotFields =
                jsonObject.getValue(JsonFields.JSON_SNAPSHOT_FIELDS).orElseGet(JsonArray::empty);
        return new SudoStreamSnapshots(burst, timeoutMillis, lowerBound, snapshotFields, dittoHeaders);
    }

    /**
     * Create a copy of this command with a lower-bound set. The lower bound must be a full PID consisting of a prefix
     * and an entity ID.
     *
     * @param lowerBound the lower bound.
     * @return a copy of this command with lower-bound set.
     */
    public SudoStreamSnapshots withLowerBound(final EntityId lowerBound) {
        return new SudoStreamSnapshots(burst, timeoutMillis, lowerBound, snapshotFields, getDittoHeaders());
    }

    /**
     * Return the lower-bound PID to resume a stream.
     *
     * @return the lower-bound PID.
     */
    public EntityId getLowerBound() {
        return lowerBound;
    }

    /**
     * Return whether the command has a non-empty lower bound.
     *
     * @return whether the command has a non-empty lower bound.
     */
    public boolean hasNonEmptyLowerBound() {
        return !lowerBound.isDummy();
    }

    /**
     * Return snapshot fields to request for each streamed snapshot.
     *
     * @return the requested snapshot fields.
     */
    public JsonArray getSnapshotFields() {
        return snapshotFields;
    }

    @Override
    public int getBurst() {
        return burst;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.JSON_BURST, burst, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_TIMEOUT_MILLIS, timeoutMillis, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_SNAPSHOT_FIELDS, snapshotFields, predicate);
        if (!lowerBound.isDummy()) {
            jsonObjectBuilder.set(JsonFields.JSON_LOWER_BOUND, lowerBound.toString(), predicate);
        }
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoStreamSnapshots setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SudoStreamSnapshots(burst, timeoutMillis, lowerBound, snapshotFields, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), burst, timeoutMillis, lowerBound, snapshotFields);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof SudoStreamSnapshots) {
            final SudoStreamSnapshots that = (SudoStreamSnapshots) obj;
            return burst == that.burst &&
                    timeoutMillis == that.timeoutMillis &&
                    Objects.equals(lowerBound, that.lowerBound) &&
                    Objects.equals(snapshotFields, that.snapshotFields) &&
                    super.equals(that);
        } else {
            return false;
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoStreamSnapshots;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", burst=" + burst
                + ", timeoutMillis=" + timeoutMillis
                + ", lowerBound=" + lowerBound
                + ", snapshotFields=" + snapshotFields
                + "]";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return TYPE;
    }

    static final class JsonFields {

        static final JsonFieldDefinition<Integer> JSON_BURST =
                JsonFactory.newIntFieldDefinition("payload/burst", REGULAR, V_2);

        static final JsonFieldDefinition<Long> JSON_TIMEOUT_MILLIS =
                JsonFactory.newLongFieldDefinition("payload/timeoutMillis", REGULAR, V_2);

        static final JsonFieldDefinition<String> JSON_LOWER_BOUND =
                JsonFactory.newStringFieldDefinition("payload/lowerBound", REGULAR, V_2);

        static final JsonFieldDefinition<JsonArray> JSON_SNAPSHOT_FIELDS =
                JsonFactory.newJsonArrayFieldDefinition("payload/fields", REGULAR, V_2);
    }
}
