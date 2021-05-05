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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TestConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


/**
 * Parameterized test for {@link ConnectivityAnnouncementMappingStrategies} verifying the correct mapper is found for
 * each known {@link ConnectivityAnnouncement} type.
 */
@RunWith(Parameterized.class)
public final class ConnectivityAnnouncementMappingStrategiesTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("bumlux");

    private static final ConnectionOpenedAnnouncement connectionOpened = ConnectionOpenedAnnouncement.of(CONNECTION_ID,
            Instant.now(), TestConstants.HEADERS_V_2);
    private static final ConnectionClosedAnnouncement connectionClosed = ConnectionClosedAnnouncement.of(CONNECTION_ID,
            Instant.now(), TestConstants.HEADERS_V_2);

    @Parameterized.Parameters(name = "{1} | expected: {2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                testParam("_/bumlux/connections/announcements/opened", ConnectionOpenedAnnouncement.TYPE,
                        connectionOpened),
                testParam("_/bumlux/connections/announcements/closed", ConnectionClosedAnnouncement.TYPE,
                        connectionClosed)
        );
    }

    private static Object[] testParam(final String topicPath, final String announcementType,
            final ConnectivityAnnouncement<?> announcement) {
        return new Object[]{topicPath, announcementType, announcement};
    }

    @Parameterized.Parameter
    public String topicPath;

    @Parameterized.Parameter(1)
    public String announcementType;

    @Parameterized.Parameter(2)
    public ConnectivityAnnouncement<?> expectedAnnouncement;

    @Test
    public void testAdaptableIsCorrectlyAdaptedToAnnouncement() {
        final Adaptable adaptable = TestConstants.adaptable(ProtocolFactory.newTopicPath(topicPath),
                JsonPointer.empty(), expectedAnnouncement.toJson());
        final ConnectivityAnnouncementMappingStrategies underTest =
                ConnectivityAnnouncementMappingStrategies.getInstance();

        final JsonifiableMapper<ConnectivityAnnouncement<?>> mapper = underTest.find(announcementType);
        assertThat(mapper).isNotNull();
        assertThat(mapper.map(adaptable)).isEqualTo(expectedAnnouncement);
    }

}
