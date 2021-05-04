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
package org.eclipse.ditto.internal.models.streaming;

import static org.eclipse.ditto.base.model.json.FieldType.REGULAR;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

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
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which starts a stream of persistence IDs with their latest snapshot revisions.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = StreamingMessage.TYPE_PREFIX, name = SudoStreamPids.NAME)
public final class SudoStreamPids extends AbstractCommand<SudoStreamPids> implements StartStreamRequest {

    static final String NAME = "SudoStreamPids";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<Integer> JSON_BURST =
            JsonFactory.newIntFieldDefinition("payload/burst", REGULAR, V_2);

    static final JsonFieldDefinition<Long> JSON_TIMEOUT_MILLIS =
            JsonFactory.newLongFieldDefinition("payload/timeoutMillis", REGULAR, V_2);

    static final JsonFieldDefinition<JsonObject> JSON_LOWER_BOUND =
            JsonFactory.newJsonObjectFieldDefinition("payload/lowerBound", REGULAR, V_2);

    private final int burst;

    private final long timeoutMillis;

    private final EntityIdWithRevision<?> lowerBound;

    private SudoStreamPids(final Integer burst, final Long timeoutMillis, final EntityIdWithRevision<?> lowerBound,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        this.burst = burst;
        this.timeoutMillis = timeoutMillis;
        this.lowerBound = lowerBound;
    }

    /**
     * Creates a new {@code SudoStreamPids} command.
     *
     * @param burst the amount of elements to be collected per message
     * @param timeoutMillis maximum time to wait for acknowledgement of each stream element.
     * @param dittoHeaders the command headers of the request.
     * @param entityType the type of the entity which should be streamed
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamPids of(final Integer burst, final Long timeoutMillis,
            final DittoHeaders dittoHeaders, final EntityType entityType) {

        return new SudoStreamPids(burst, timeoutMillis, LowerBound.empty(entityType), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoStreamSnapshotRevisions} from a JSON object.
     *
     * @param jsonObject the JSON representation of the command.
     * @param dittoHeaders the optional command headers of the request.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SudoStreamPids fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final int burst = jsonObject.getValueOrThrow(JSON_BURST);
        final long timeoutMillis = jsonObject.getValueOrThrow(JSON_TIMEOUT_MILLIS);
        final EntityIdWithRevision<EntityId> lowerBound =
                LowerBound.fromJson(jsonObject.getValueOrThrow(JSON_LOWER_BOUND));
        return new SudoStreamPids(burst, timeoutMillis, lowerBound, dittoHeaders);
    }

    /**
     * Create a copy of this command with a lower-bound set. The lower bound must be a full PID consisting of a prefix
     * and an entity ID.
     *
     * @param lowerBound the lower bound.
     * @return a copy of this command with lower-bound set.
     */
    public SudoStreamPids withLowerBound(final EntityIdWithRevision<?> lowerBound) {
        return new SudoStreamPids(burst, timeoutMillis, lowerBound, getDittoHeaders());
    }

    /**
     * Return the lower-bound PID to resume a stream.
     *
     * @return the lower-bound PID.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    public EntityIdWithRevision getLowerBound() {
        return lowerBound;
    }

    /**
     * Return whether the command has a non-empty lower bound.
     *
     * @return whether the command has a non-empty lower bound.
     */
    public boolean hasNonEmptyLowerBound() {
        return !lowerBound.getEntityId().equals(LowerBound.emptyEntityId(lowerBound.getEntityId().getEntityType()));
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_BURST, burst, predicate);
        jsonObjectBuilder.set(JSON_TIMEOUT_MILLIS, timeoutMillis, predicate);
        jsonObjectBuilder.set(JSON_LOWER_BOUND, lowerBound.toJson(), predicate);
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
    public SudoStreamPids setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SudoStreamPids(burst, timeoutMillis, lowerBound, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), burst, timeoutMillis, lowerBound);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof SudoStreamPids) {
            final SudoStreamPids that = (SudoStreamPids) obj;
            return burst == that.burst && timeoutMillis == that.timeoutMillis && lowerBound.equals(that.lowerBound) &&
                    super.equals(that);
        } else {
            return false;
        }
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoStreamPids;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", burst=" + burst
                + ", timeoutMillis=" + timeoutMillis
                + ", lowerBound=" + lowerBound
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

}
