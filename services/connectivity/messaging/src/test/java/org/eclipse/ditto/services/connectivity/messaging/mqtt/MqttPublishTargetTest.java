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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

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
        assertThat(MqttPublishTarget.of(TOPIC).getTopic()).isEqualTo(TOPIC);
    }

    @Test(expected = NullPointerException.class)
    public void testNull() {
        MqttPublishTarget.of(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() {
        MqttPublishTarget.of("");
    }

}
