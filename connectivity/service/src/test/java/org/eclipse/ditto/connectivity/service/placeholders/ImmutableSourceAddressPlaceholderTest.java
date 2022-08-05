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
package org.eclipse.ditto.connectivity.service.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Tests {@link ImmutableSourceAddressPlaceholder}.
 */
public class ImmutableSourceAddressPlaceholderTest {

    private static final String SOME_MQTT_TOPIC = "some/mqtt/topic";

    @Test
    public void testImmutability() {
        assertInstancesOf(ImmutableSourceAddressPlaceholder.class, areImmutable());
    }

    @Test
    public void testReplaceTopic() {
        assertThat(ImmutableSourceAddressPlaceholder.INSTANCE.resolveValues(SOME_MQTT_TOPIC, "address")).contains(SOME_MQTT_TOPIC);
    }

    @Test
    public void testResultIsEmptyForUnknownPlaceholder() {
        assertThat(ImmutableSourceAddressPlaceholder.INSTANCE.resolveValues(SOME_MQTT_TOPIC, "invalid")).isEmpty();
    }
}
