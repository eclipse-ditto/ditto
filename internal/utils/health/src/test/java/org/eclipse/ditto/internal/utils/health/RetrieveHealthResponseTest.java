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
package org.eclipse.ditto.internal.utils.health;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link RetrieveHealthResponse}.
 */
public final class RetrieveHealthResponseTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(RetrieveHealthResponse.class, areImmutable(),
                provided(StatusInfo.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveHealthResponse.class)
                .usingGetClass()
                .withPrefabValues(StatusInfo.class, StatusInfo.fromStatus(StatusInfo.Status.UP, "up"),
                        StatusInfo.fromStatus(StatusInfo.Status.DOWN, "down"))
                .verify();
    }

}
