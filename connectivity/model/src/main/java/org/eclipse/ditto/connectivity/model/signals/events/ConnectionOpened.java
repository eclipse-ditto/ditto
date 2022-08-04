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
package org.eclipse.ditto.connectivity.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.connectivity.model.Connection} was opened.
 */
@Immutable
@JsonParsableEvent(name = ConnectionOpened.NAME, typePrefix= ConnectivityEvent.TYPE_PREFIX)
public final class ConnectionOpened extends AbstractConnectivityEvent<ConnectionOpened>
        implements ConnectivityEvent<ConnectionOpened> {

    /**
     * Name of this event.
     */
    public static final String NAME = "connectionOpened";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private ConnectionOpened(final ConnectionId connectionId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, connectionId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Returns a new {@code ConnectionOpened} event.
     *
     * @param connectionId the identifier of the opened Connection.
     * @param revision the revision of the Connection.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the event.
     * @throws NullPointerException if {@code connectionId} or {@code dittoHeaders} are {@code null}.
     */
    public static ConnectionOpened of(final ConnectionId connectionId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        checkNotNull(connectionId, "Connection ID");
        return new ConnectionOpened(connectionId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a {@code ConnectionOpened} event from a JSON string.
     *
     * @param jsonString the JSON string of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ConnectionOpened fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a {@code ConnectionOpened} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ConnectionOpened fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ConnectionOpened>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String readConnectionId = jsonObject.getValueOrThrow(
                            ConnectivityEvent.JsonFields.CONNECTION_ID);
                    final ConnectionId connectionId = ConnectionId.of(readConnectionId);
                    return of(connectionId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ConnectionOpened setRevision(final long revision) {
        return of(getEntityId(), revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public ConnectionOpened setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        // nothing to append
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ConnectionOpened);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
