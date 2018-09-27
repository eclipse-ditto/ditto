/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity;

import java.nio.ByteBuffer;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilter;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link UnmodifiableExternalMessage}.
 */
public final class UnmodifiableExternalMessageTest {

    @Test
    public void assertImmutability() {
        // The field "bytePayload" is mutable.
        // Assume the user never modifies it.
        MutabilityAssert.assertInstancesOf(UnmodifiableExternalMessage.class, MutabilityMatchers.areImmutable(),
                AllowedReason.assumingFields("bytePayload").areNotModifiedAndDoNotEscape(),
                AllowedReason.provided(ByteBuffer.class, AuthorizationContext.class, Adaptable.class,
                        EnforcementFilter.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(UnmodifiableExternalMessage.class)
                .withPrefabValues(ByteBuffer.class,
                        ByteBuffer.wrap("red" .getBytes()),
                        ByteBuffer.wrap("black" .getBytes()))
                .usingGetClass()
                .verify();
    }

}
