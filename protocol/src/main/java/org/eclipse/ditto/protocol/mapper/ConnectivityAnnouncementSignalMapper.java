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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Signal mapper implementation for {@link ConnectivityAnnouncement}s.
 *
 * @since 2.1.0
 */
final class ConnectivityAnnouncementSignalMapper extends AbstractSignalMapper<ConnectivityAnnouncement<?>> {

    @Override
    TopicPath getTopicPath(final ConnectivityAnnouncement<?> signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilderFromName(signal.getEntityId().toString())
                .connections()
                .announcements()
                .name(signal.getName())
                .build();
    }

    @Override
    void enhancePayloadBuilder(final ConnectivityAnnouncement<?> signal, final PayloadBuilder payloadBuilder) {
        if (signal instanceof ConnectionOpenedAnnouncement) {
            final ConnectionOpenedAnnouncement announcement = (ConnectionOpenedAnnouncement) signal;
            final JsonObject payload = getConnectionOpenedAnnouncementPayload(announcement);
            payloadBuilder.withValue(payload).build();
        }
        if (signal instanceof ConnectionClosedAnnouncement) {
            final ConnectionClosedAnnouncement announcement = (ConnectionClosedAnnouncement) signal;
            final JsonObject payload = getConnectionClosedAnnouncementPayload(announcement);
            payloadBuilder.withValue(payload).build();
        }
        // otherwise be tolerant and don't expand payload instead of throwing an exception
    }

    private static JsonObject getConnectionOpenedAnnouncementPayload(final ConnectionOpenedAnnouncement announcement) {
        return JsonObject.newBuilder()
                .set(ConnectionOpenedAnnouncement.JsonFields.OPENED_AT, announcement.getOpenedAt().toString())
                .build();
    }

    private static JsonObject getConnectionClosedAnnouncementPayload(final ConnectionClosedAnnouncement announcement) {
        return JsonObject.newBuilder()
                .set(ConnectionClosedAnnouncement.JsonFields.CLOSED_AT, announcement.getClosedAt().toString())
                .build();
    }

}
