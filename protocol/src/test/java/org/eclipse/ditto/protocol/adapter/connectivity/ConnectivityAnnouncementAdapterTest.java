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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ConnectivityAnnouncementAdapter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ConnectivityAnnouncementAdapterTest {

    @Mock
    private HeaderTranslator headerTranslator;

    @Test
    public void createInstance() {
        final ConnectivityAnnouncementAdapter announcementAdapter =
                ConnectivityAnnouncementAdapter.of(headerTranslator);

        assertThat(announcementAdapter.getActions()).isEmpty();
        assertThat(announcementAdapter.getCriteria()).containsExactlyInAnyOrder(TopicPath.Criterion.ANNOUNCEMENTS);
        assertThat(announcementAdapter.isForResponses()).isFalse();
    }

    @Test
    public void getTypeMapsFromTopicPathModelToServiceModel() {
        final Adaptable adaptable =
                Adaptable.newBuilder(ProtocolFactory.newTopicPath("_/id/connections/announcements/opened"))
                        .withPayload(Payload.newBuilder().build())
                        .build();

        final ConnectivityAnnouncementAdapter announcementAdapter =
                ConnectivityAnnouncementAdapter.of(headerTranslator);

        assertThat(announcementAdapter.getType(adaptable)).isEqualTo("connectivity.announcements:opened");
    }

}
