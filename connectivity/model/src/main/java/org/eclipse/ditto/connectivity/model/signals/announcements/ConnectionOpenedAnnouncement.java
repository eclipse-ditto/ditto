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
 * Announcement that a connection was opened by a user (by setting its target status to 'open').
 *
 * @since 2.1.0
 */
@Immutable
@JsonParsableAnnouncement(type = ConnectionOpenedAnnouncement.TYPE)
public final class ConnectionOpenedAnnouncement extends AbstractConnectivityAnnouncement<ConnectionOpenedAnnouncement> {

    private static final String NAME = "opened";

    /**
     * Type of this announcement.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Instant openedAt;

    private ConnectionOpenedAnnouncement(final ConnectionId connectionId, final Instant openedAt,
            final DittoHeaders dittoHeaders) {
        super(connectionId, dittoHeaders);
        this.openedAt = checkNotNull(openedAt, "openedAt");
    }

    /**
     * Create a announcement for connection opening.
     *
     * @param connectionId the connection ID.
     * @param openedAt when the connection was opened.
     * @param dittoHeaders headers of the announcement.
     * @return the announcement.
     */
    public static ConnectionOpenedAnnouncement of(final ConnectionId connectionId, final Instant openedAt,
            final DittoHeaders dittoHeaders) {

        return new ConnectionOpenedAnnouncement(connectionId, openedAt, dittoHeaders);
    }

    /**
     * Deserialize a connection-opened announcement from JSON.
     *
     * @param jsonObject the serialized JSON.
     * @param dittoHeaders the Ditto headers.
     * @return the deserialized {@code ConnectionOpenedAnnouncement}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ConnectionOpenedAnnouncement' format.
     */
    public static ConnectionOpenedAnnouncement fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final ConnectionId connectionId = deserializeConnectionId(jsonObject);
        final Instant openedAt = deserializeOpenedAt(jsonObject);
        return of(connectionId, openedAt, dittoHeaders);
    }

    private static Instant deserializeOpenedAt(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.OPENED_AT;
        final String openedAtJsonValue = jsonObject.getValueOrThrow(fieldDefinition);
        try {
            return Instant.parse(openedAtJsonValue);
        } catch (final DateTimeParseException e) {
            throw getJsonParseExceptionBuilder(fieldDefinition, Instant.class, e)
                    .description("Opened timestamp must be provided as ISO-8601 formatted char sequence.")
                    .build();
        }
    }

    @Override
    public ConnectionOpenedAnnouncement setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ConnectionOpenedAnnouncement(getEntityId(), openedAt, dittoHeaders);
    }

    @Override
    protected void appendConnectivityAnnouncementPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.OPENED_AT, openedAt.toString(), predicate);
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
     * Get the timestamp when the connection was opened.
     *
     * @return the connection open timestamp.
     */
    public Instant getOpenedAt() {
        return openedAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ", openedAt=" + openedAt +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            final ConnectionOpenedAnnouncement that = (ConnectionOpenedAnnouncement) other;
            return Objects.equals(openedAt, that.openedAt);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(openedAt, super.hashCode());
    }

    /**
     * JSON fields of this announcement's payload for use in the Ditto protocol.
     */
    public static final class JsonFields {

        /**
         * JSON field for the timestamp when the connection was opened.
         */
        public static final JsonFieldDefinition<String> OPENED_AT =
                JsonFactory.newStringFieldDefinition("openedAt", JsonSchemaVersion.V_2, FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
