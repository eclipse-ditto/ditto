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
package org.eclipse.ditto.connectivity.api;

import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MappableOutboundSignalTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(MappableOutboundSignal.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(OutboundSignal.class, PayloadMapping.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MappableOutboundSignal.class)
                .usingGetClass()
                .verify();
    }

}
