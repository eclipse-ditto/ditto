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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link KafkaPublisherActorFactory}.
 */
public final class DefaultKafkaPublisherActorFactoryTest {

    private DefaultKafkaPublisherActorFactory underTest;

    @Before
    public void setUp() {
        underTest = DefaultKafkaPublisherActorFactory.getInstance();
    }

    @Test
    public void testName() {
        assertThat(underTest.getActorName()).isEqualTo(KafkaPublisherActor.ACTOR_NAME);
    }

    @Test
    public void testProps() {
        assertThat(underTest.props(null,
                null,
                false,
                mock(ConnectivityStatusResolver.class),
                null)).isNotNull();
    }

}
