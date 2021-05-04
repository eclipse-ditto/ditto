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

package org.eclipse.ditto.connectivity.model;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableConnectionMetrics}.
 */
public class ImmutableConnectionMetricsTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableConnectionMetrics.class)
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableConnectionMetrics.class,
                areImmutable(),
                provided(AddressMetric.class).isAlsoImmutable());
    }
}
