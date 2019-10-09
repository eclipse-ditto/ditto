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
package org.eclipse.ditto.services.models.connectivity;

import java.nio.ByteBuffer;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.placeholders.EnforcementFilter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.TopicPath;
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
                AllowedReason.provided(DittoHeaders.class).isAlsoImmutable(),
                AllowedReason.assumingFields("bytePayload").areNotModifiedAndDoNotEscape(),
                AllowedReason.provided(ByteBuffer.class, AuthorizationContext.class, Adaptable.class,
                        EnforcementFilter.class, HeaderMapping.class, PayloadMapping.class, TopicPath.class)
                        .areAlsoImmutable());
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
