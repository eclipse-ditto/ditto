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

import org.eclipse.ditto.protocol.Adaptable;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MappedOutboundSignalTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(MappedOutboundSignal.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Adaptable.class, ExternalMessage.class, OutboundSignal.class)
                        .areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MappedOutboundSignal.class)
                .usingGetClass()
                .verify();
    }

}
