/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MqttPublishTargetTest {

    private static final String TOPIC = "eclipse/ditto/mqtt/topic";

    @Test
    public void testImmutability() {
        assertInstancesOf(MqttPublishTarget.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MqttPublishTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void test() {
        assertThat(MqttPublishTarget.of(TOPIC, 0).getTopic()).isEqualTo(TOPIC);
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        MqttPublishTarget.of(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() {
        MqttPublishTarget.of("", 0);
    }

}
