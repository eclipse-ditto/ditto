/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import static org.eclipse.ditto.base.model.json.FieldType.REGULAR;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command which starts a stream of journal entries as persisted events for a given EntityId.
 * Corresponds to the reactive-streams signal {@code Publisher#subscribe(Subscriber)}.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableCommand(typePrefix = StreamingSubscriptionCommand.TYPE_PREFIX, name = SubscribeForPersistedEvents.NAME)
public final class SubscribeForPersistedEvents extends AbstractStreamingSubscriptionCommand<SubscribeForPersistedEvents>
        implements StreamingSubscriptionCommand<SubscribeForPersistedEvents> {

    /**
     * The name of this streaming subscription command.
     */
    public static final String NAME = "subscribeForPersistedEvents";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final long fromHistoricalRevision;
    private final long toHistoricalRevision;

    @Nullable private final Instant fromHistoricalTimestamp;
    @Nullable private final Instant toHistoricalTimestamp;
    @Nullable private final String prefix;

    private SubscribeForPersistedEvents(final EntityId entityId,
            final JsonPointer resourcePath,
            final long fromHistoricalRevision,
            final long toHistoricalRevision,
            @Nullable final Instant fromHistoricalTimestamp,
            @Nullable final Instant toHistoricalTimestamp,
            @Nullable final String prefix,
            final DittoHeaders dittoHeaders) {

        super(TYPE, entityId, resourcePath, dittoHeaders);
        this.fromHistoricalRevision = fromHistoricalRevision;
        this.toHistoricalRevision = toHistoricalRevision;
        this.fromHistoricalTimestamp = fromHistoricalTimestamp;
        this.toHistoricalTimestamp = toHistoricalTimestamp;
        this.prefix = prefix;
    }

    /**
     * Creates a new {@code SudoStreamSnapshots} command based on "from" and "to" {@code long} revisions.
     *
     * @param entityId the entityId that should be streamed.
     * @param resourcePath the resource path for which to stream events.
     * @param fromHistoricalRevision the revision to start the streaming from.
     * @param toHistoricalRevision the revision to stop the streaming at.
     * @param dittoHeaders the command headers of the request.
     * @return the command.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static SubscribeForPersistedEvents of(final EntityId entityId,
            final JsonPointer resourcePath,
            final long fromHistoricalRevision,
            final long toHistoricalRevision,
            final DittoHeaders dittoHeaders) {

        return new SubscribeForPersistedEvents(entityId,
                resourcePath,
                fromHistoricalRevision,
                toHistoricalRevision,
                null,
                null,
                null,
                dittoHeaders);
    }

    /**
     * Creates a new {@code SudoStreamSnapshots} command based on "from" and "to" {@code Instant} timestamps.
     *
     * @param entityId the entityId that should be streamed.
     * @param resourcePath the resource path for which to stream events.
     * @param fromHistoricalTimestamp the timestamp to start the streaming from.
     * @param toHistoricalTimestamp the timestamp to stop the streaming at.
     * @param dittoHeaders the command headers of the request.
     * @return the command.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static SubscribeForPersistedEvents of(final EntityId entityId,
            final JsonPointer resourcePath,
            @Nullable final Instant fromHistoricalTimestamp,
            @Nullable final Instant toHistoricalTimestamp,
            final DittoHeaders dittoHeaders) {

        return new SubscribeForPersistedEvents(entityId,
                resourcePath,
                0L,
                Long.MAX_VALUE,
                fromHistoricalTimestamp,
                toHistoricalTimestamp,
                null,
                dittoHeaders);
    }

    /**
     * Creates a new {@code SudoStreamSnapshots} command based on "from" and "to" {@code Instant} timestamps.
     *
     * @param entityId the entityId that should be streamed.
     * @param resourcePath the resource path for which to stream events.
     * @param fromHistoricalRevision the revision to start the streaming from.
     * @param toHistoricalRevision the revision to stop the streaming at.
     * @param fromHistoricalTimestamp the timestamp to start the streaming from.
     * @param toHistoricalTimestamp the timestamp to stop the streaming at.
     * @param dittoHeaders the command headers of the request.
     * @return the command.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static SubscribeForPersistedEvents of(final EntityId entityId,
            final JsonPointer resourcePath,
            @Nullable final Long fromHistoricalRevision,
            @Nullable final Long toHistoricalRevision,
            @Nullable final Instant fromHistoricalTimestamp,
            @Nullable final Instant toHistoricalTimestamp,
            final DittoHeaders dittoHeaders) {

        return new SubscribeForPersistedEvents(entityId,
                resourcePath,
                null != fromHistoricalRevision ? fromHistoricalRevision : 0L,
                null != toHistoricalRevision ? toHistoricalRevision : Long.MAX_VALUE,
                fromHistoricalTimestamp,
                toHistoricalTimestamp,
                null,
                dittoHeaders);
    }

    /**
     * Deserializes a {@code SubscribeForPersistedEvents} from the specified {@link JsonObject} argument.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @return the deserialized {@code SubscribeForPersistedEvents}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static SubscribeForPersistedEvents fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new SubscribeForPersistedEvents(deserializeEntityId(jsonObject),
                JsonPointer.of(jsonObject.getValueOrThrow(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH)),
                jsonObject.getValueOrThrow(JsonFields.JSON_FROM_HISTORICAL_REVISION),
                jsonObject.getValueOrThrow(JsonFields.JSON_TO_HISTORICAL_REVISION),
                jsonObject.getValue(JsonFields.JSON_FROM_HISTORICAL_TIMESTAMP).map(Instant::parse).orElse(null),
                jsonObject.getValue(JsonFields.JSON_TO_HISTORICAL_TIMESTAMP).map(Instant::parse).orElse(null),
                jsonObject.getValue(JsonFields.PREFIX).orElse(null),
                dittoHeaders
        );
    }

    /**
     * Create a copy of this command with prefix set. The prefix is used to identify a streaming subscription manager
     * if multiple are deployed in the cluster.
     *
     * @param prefix the subscription ID prefix.
     * @return the new command.
     */
    public SubscribeForPersistedEvents setPrefix(@Nullable final String prefix) {
        return new SubscribeForPersistedEvents(entityId, resourcePath, fromHistoricalRevision, toHistoricalRevision,
                fromHistoricalTimestamp, toHistoricalTimestamp, prefix, getDittoHeaders());
    }

    /**
     * Returns the revision to start the streaming from.
     *
     * @return the revision to start the streaming from.
     */
    public long getFromHistoricalRevision() {
        return fromHistoricalRevision;
    }

    /**
     * Returns the timestamp to stop the streaming at.
     *
     * @return the timestamp to stop the streaming at.
     */
    public long getToHistoricalRevision() {
        return toHistoricalRevision;
    }

    /**
     * Returns the optional timestamp to start the streaming from.
     *
     * @return the optional timestamp to start the streaming from.
     */
    public Optional<Instant> getFromHistoricalTimestamp() {
        return Optional.ofNullable(fromHistoricalTimestamp);
    }

    /**
     * Returns the optional timestamp to stop the streaming at.
     *
     * @return the optional timestamp to stop the streaming at.
     */
    public Optional<Instant> getToHistoricalTimestamp() {
        return Optional.ofNullable(toHistoricalTimestamp);
    }

    /**
     * Get the prefix of subscription IDs. The prefix is used to identify a streaming subscription manager if multiple
     * are deployed in the cluster.
     *
     * @return the subscription ID prefix.
     */
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.JSON_FROM_HISTORICAL_REVISION, fromHistoricalRevision, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_TO_HISTORICAL_REVISION, toHistoricalRevision, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_TO_HISTORICAL_REVISION, toHistoricalRevision, predicate);
        if (null != fromHistoricalTimestamp) {
            jsonObjectBuilder.set(JsonFields.JSON_FROM_HISTORICAL_TIMESTAMP, fromHistoricalTimestamp.toString(),
                    predicate);
        }
        if (null != toHistoricalTimestamp) {
            jsonObjectBuilder.set(JsonFields.JSON_TO_HISTORICAL_TIMESTAMP, toHistoricalTimestamp.toString(), predicate);
        }
        getPrefix().ifPresent(thePrefix -> jsonObjectBuilder.set(JsonFields.PREFIX, thePrefix));
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public SubscribeForPersistedEvents setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubscribeForPersistedEvents(entityId, resourcePath, fromHistoricalRevision, toHistoricalRevision,
                fromHistoricalTimestamp, toHistoricalTimestamp, prefix, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId, resourcePath, fromHistoricalRevision, toHistoricalRevision,
                fromHistoricalTimestamp, toHistoricalTimestamp, prefix);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SubscribeForPersistedEvents that = (SubscribeForPersistedEvents) obj;

        return that.canEqual(this) && super.equals(that) &&
                fromHistoricalRevision == that.fromHistoricalRevision &&
                toHistoricalRevision == that.toHistoricalRevision &&
                Objects.equals(fromHistoricalTimestamp, that.fromHistoricalTimestamp) &&
                Objects.equals(toHistoricalTimestamp, that.toHistoricalTimestamp) &&
                Objects.equals(prefix, that.prefix);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubscribeForPersistedEvents;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", fromHistoricalRevision=" + fromHistoricalRevision
                + ", toHistoricalRevision=" + toHistoricalRevision
                + ", fromHistoricalTimestamp=" + fromHistoricalTimestamp
                + ", toHistoricalTimestamp=" + toHistoricalTimestamp
                + ", prefix=" + prefix
                + "]";
    }

    /**
     * This class contains definitions for all specific fields of this command's JSON representation.
     */
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        public static final JsonFieldDefinition<Long> JSON_FROM_HISTORICAL_REVISION =
                JsonFactory.newLongFieldDefinition("fromHistoricalRevision", REGULAR, V_2);

        public static final JsonFieldDefinition<Long> JSON_TO_HISTORICAL_REVISION =
                JsonFactory.newLongFieldDefinition("toHistoricalRevision", REGULAR, V_2);

        public static final JsonFieldDefinition<String> JSON_FROM_HISTORICAL_TIMESTAMP =
                JsonFactory.newStringFieldDefinition("fromHistoricalTimestamp", REGULAR, V_2);

        public static final JsonFieldDefinition<String> JSON_TO_HISTORICAL_TIMESTAMP =
                JsonFactory.newStringFieldDefinition("toHistoricalTimestamp", REGULAR, V_2);

        static final JsonFieldDefinition<String> PREFIX =
                JsonFactory.newStringFieldDefinition("prefix", REGULAR, V_2);
    }

}
