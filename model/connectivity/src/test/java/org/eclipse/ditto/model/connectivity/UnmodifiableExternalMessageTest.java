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
package org.eclipse.ditto.model.connectivity;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link UnmodifiableExternalMessage}.
 */
public final class UnmodifiableExternalMessageTest {

    @Test
    public void assertImmutability() {
        // The field "bytePayload" is mutable.
        // Assume the user never modifies it.
        assertInstancesOf(UnmodifiableExternalMessage.class, areImmutable(),
                assumingFields("bytePayload").areNotModifiedAndDoNotEscape(),
                provided(ByteBuffer.class, AuthorizationContext.class, Adaptable.class).areAlsoImmutable());
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
