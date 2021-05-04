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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.JmsTopic;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AmqpTargetTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AmqpTarget.class).usingGetClass().verify();
    }

    @Test
    public void testCreateQueue() {
        assertThat(AmqpTarget.fromTargetAddress("queue").getJmsDestination()).isEqualTo(new JmsQueue("queue"));
    }

    @Test
    public void testCreateTopic() {
        assertThat(AmqpTarget.fromTargetAddress("topic://topic").getJmsDestination()).isEqualTo(new JmsTopic("topic"));
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        AmqpTarget.fromTargetAddress(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() {
        AmqpTarget.fromTargetAddress("");
    }
}
