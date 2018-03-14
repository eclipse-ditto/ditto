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
package org.eclipse.ditto.signals.events.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link Connection} was opened.
 */
@Immutable
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

    private ConnectionOpened(final String connectionId, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        super(TYPE, connectionId, timestamp, dittoHeaders);
    }

    /**
     * Returns a new {@code ConnectionOpened} event.
     *
     * @param connectionId the identifier of the opened Connection.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionOpened of(final String connectionId, final DittoHeaders dittoHeaders) {
        return of(connectionId, null, dittoHeaders);
    }

    /**
     * Returns a new {@code ConnectionOpened} event.
     *
     * @param connectionId the identifier of the opened Connection.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if {@code connectionId} or {@code dittoHeaders} are {@code null}.
     */
    public static ConnectionOpened of(final String connectionId, @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        return new ConnectionOpened(connectionId, timestamp, dittoHeaders);
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
                .deserialize((revision, timestamp) -> {
                    final String readConnectionId = jsonObject.getValueOrThrow(JsonFields.CONNECTION_ID);
                    return of(readConnectionId, timestamp, dittoHeaders);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ConnectionOpened setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getConnectionId(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
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
