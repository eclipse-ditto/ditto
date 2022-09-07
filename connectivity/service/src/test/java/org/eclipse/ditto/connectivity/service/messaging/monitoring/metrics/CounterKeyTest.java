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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.connectivity.model.MetricType;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CounterKeyTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(CounterKey.class, areImmutable(), AllowedReason.provided(MetricType.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CounterKey.class)
                .usingGetClass()
                .verify();
    }

}
