/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.announcements;


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableAnnouncement;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Announcement that a connection was closed by a user (by setting its target status to 'closed').
 *
 * @since 2.1.0
 */
@Immutable
@JsonParsableAnnouncement(type = ConnectionClosedAnnouncement.TYPE)
public final class ConnectionClosedAnnouncement extends AbstractConnectivityAnnouncement<ConnectionClosedAnnouncement> {

    private static final String NAME = "closed";

    /**
     * Type of this announcement.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Instant closedAt;

    private ConnectionClosedAnnouncement(final ConnectionId connectionId, final Instant closedAt,
            final DittoHeaders dittoHeaders) {
        super(connectionId, dittoHeaders);
        this.closedAt = checkNotNull(closedAt, "closedAt");
    }

    /**
     * Create a announcement for connection closing.
     *
     * @param connectionId the connection ID.
     * @param closedAt when the connection was closed.
     * @param dittoHeaders headers of the announcement.
     * @return the announcement.
     */
    public static ConnectionClosedAnnouncement of(final ConnectionId connectionId, final Instant closedAt,
            final DittoHeaders dittoHeaders) {

        return new ConnectionClosedAnnouncement(connectionId, closedAt, dittoHeaders);
    }

    /**
     * Deserialize a connection-closed announcement from JSON.
     *
     * @param jsonObject the serialized JSON.
     * @param dittoHeaders the Ditto headers.
     * @return the deserialized {@code ConnectionClosedAnnouncement}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ConnectionClosedAnnouncement' format.
     */
    public static ConnectionClosedAnnouncement fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final ConnectionId connectionId = deserializeConnectionId(jsonObject);
        final Instant closedAt = deserializeClosedAt(jsonObject);
        return of(connectionId, closedAt, dittoHeaders);
    }

    private static Instant deserializeClosedAt(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.CLOSED_AT;
        final String closeddAtJsonValue = jsonObject.getValueOrThrow(fieldDefinition);
        try {
            return Instant.parse(closeddAtJsonValue);
        } catch (final DateTimeParseException e) {
            throw getJsonParseExceptionBuilder(fieldDefinition, Instant.class, e)
                    .description("Closed timestamp must be provided as ISO-8601 formatted char sequence.")
                    .build();
        }
    }

    @Override
    public ConnectionClosedAnnouncement setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ConnectionClosedAnnouncement(getEntityId(), closedAt, dittoHeaders);
    }

    @Override
    protected void appendConnectivityAnnouncementPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.CLOSED_AT, closedAt.toString(), predicate);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Get the timestamp when the connection was closed.
     *
     * @return the connection close timestamp.
     */
    public Instant getClosedAt() {
        return closedAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ", closedAt=" + closedAt +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            final ConnectionClosedAnnouncement that = (ConnectionClosedAnnouncement) other;
            return Objects.equals(closedAt, that.closedAt);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(closedAt, super.hashCode());
    }

    /**
     * JSON fields of this announcement's payload for use in the Ditto protocol.
     */
    public static final class JsonFields {

        /**
         * JSON field for the timestamp when the connection was closed.
         */
        public static final JsonFieldDefinition<String> CLOSED_AT =
                JsonFactory.newStringFieldDefinition("closedAt", JsonSchemaVersion.V_2, FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
