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
package org.eclipse.ditto.protocol.adapter.connectivity;

import static org.eclipse.ditto.protocol.TestConstants.Connectivity.CONNECTION_ID;
import static org.eclipse.ditto.protocol.TestConstants.Connectivity.TIMESTAMP;
import static org.eclipse.ditto.protocol.TestConstants.Connectivity.TopicPaths;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ParametrizedCommandAdapterTest;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Parameterized test for {@link ConnectivityAnnouncementAdapter} verifying each known {@link ConnectivityAnnouncement}
 * is correctly mapped to its corresponding {@link Adaptable}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedConnectivityAnnouncementAdapterTest
        extends ParametrizedCommandAdapterTest<ConnectivityAnnouncement<?>> implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(connectionOpened(), connectionClosed());
    }

    private ConnectivityAnnouncementAdapter underTest;

    @Before
    public void setUp() {
        underTest = ConnectivityAnnouncementAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected AbstractConnectivityAdapter<ConnectivityAnnouncement<?>> underTest() {
        return underTest;
    }

    @Override
    protected TopicPath.Channel defaultChannel() {
        return TopicPath.Channel.NONE;
    }

    private static TestParameter<ConnectivityAnnouncement<?>> connectionOpened() {
        final ConnectionOpenedAnnouncement announcement =
                ConnectionOpenedAnnouncement.of(CONNECTION_ID, TIMESTAMP, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.announcement("opened"),
                        EMPTY_PATH,
                        announcement.toJson(onlyDefinitions(ConnectionOpenedAnnouncement.JsonFields.OPENED_AT))
                );
        return TestParameter.of("connectionOpened", adaptable, announcement);
    }

    private static TestParameter<ConnectivityAnnouncement<?>> connectionClosed() {
        final ConnectionClosedAnnouncement announcement =
                ConnectionClosedAnnouncement.of(CONNECTION_ID, TIMESTAMP, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.announcement("closed"),
                        EMPTY_PATH,
                        announcement.toJson(onlyDefinitions(ConnectionClosedAnnouncement.JsonFields.CLOSED_AT)));
        return TestParameter.of("connectionClosed", adaptable, announcement);
    }

    private static Predicate<JsonField> onlyDefinitions(final JsonFieldDefinition<?>... fieldDefinitions) {
        final Collection<JsonFieldDefinition<?>> definitionsList = Arrays.asList(fieldDefinitions);
        return field -> field.getDefinition().filter(definitionsList::contains).isPresent();
    }

}
