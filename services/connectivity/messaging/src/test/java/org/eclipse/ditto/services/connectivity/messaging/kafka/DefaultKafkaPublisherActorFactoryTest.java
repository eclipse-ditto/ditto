/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActorFactory}.
 */
public class DefaultKafkaPublisherActorFactoryTest {

    private DefaultKafkaPublisherActorFactory underTest = DefaultKafkaPublisherActorFactory.getInstance();

    @Test
    public void testName() {
        assertThat(underTest.name()).isEqualTo(KafkaPublisherActor.ACTOR_NAME);
    }

    @Test
    public void testProps() {
        assertThat(underTest.props(null, null, null, null, false))
                .isNotNull();
    }

}
