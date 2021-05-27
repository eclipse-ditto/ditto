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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for connectivity announcements.
 *
 * @since 2.1.0
 */
final class ConnectivityAnnouncementMappingStrategies
        extends AbstractConnectivityMappingStrategies<ConnectivityAnnouncement<?>> {

    private static final ConnectivityAnnouncementMappingStrategies
            INSTANCE = new ConnectivityAnnouncementMappingStrategies();

    private ConnectivityAnnouncementMappingStrategies() {
        super(initMappingStrategies());
    }

    /**
     * Get the unique instance of this class.
     *
     * @return the instance.
     */
    public static ConnectivityAnnouncementMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<ConnectivityAnnouncement<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<ConnectivityAnnouncement<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(ConnectionOpenedAnnouncement.TYPE,
                ConnectivityAnnouncementMappingStrategies::toConnectionOpenedAnnouncement);
        mappingStrategies.put(ConnectionClosedAnnouncement.TYPE,
                ConnectivityAnnouncementMappingStrategies::toConnectionClosedAnnouncement);
        return mappingStrategies;
    }

    private static ConnectionOpenedAnnouncement toConnectionOpenedAnnouncement(final Adaptable adaptable) {
        final ConnectionId connectionId = connectionIdFromTopicPath(adaptable.getTopicPath());
        final DittoHeaders dittoHeaders = dittoHeadersFrom(adaptable);
        final JsonObject payload = getValueFromPayload(adaptable);
        final Instant openedAt = deserializeInstant(payload, ConnectionOpenedAnnouncement.JsonFields.OPENED_AT);
        return ConnectionOpenedAnnouncement.of(connectionId, openedAt, dittoHeaders);
    }

    private static ConnectionClosedAnnouncement toConnectionClosedAnnouncement(final Adaptable adaptable) {
        final ConnectionId connectionId = connectionIdFromTopicPath(adaptable.getTopicPath());
        final DittoHeaders dittoHeaders = dittoHeadersFrom(adaptable);
        final JsonObject payload = getValueFromPayload(adaptable);
        final Instant closedAt = deserializeInstant(payload, ConnectionClosedAnnouncement.JsonFields.CLOSED_AT);
        return ConnectionClosedAnnouncement.of(connectionId, closedAt, dittoHeaders);
    }

    private static Instant deserializeInstant(final JsonObject jsonObject,
            final JsonFieldDefinition<String> fieldDefinition) {
        final String instantString = jsonObject.getValueOrThrow(fieldDefinition);
        try {
            return Instant.parse(instantString);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize field <{0}> with value <{1}> as {2}: {3}",
                            fieldDefinition.getPointer(),
                            instantString,
                            Instant.class,
                            e.getMessage()))
                    .description("Timestamp must be provided as ISO-8601 formatted char sequence.")
                    .build();
        }
    }

}
