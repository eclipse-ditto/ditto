/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model.signals.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SubscriptionComplete}.
 */
public final class SubscriptionCompleteTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubscriptionComplete.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscriptionComplete.class).withRedefinedSuperclass().verify();
    }

    @Test
    public void serialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubscriptionComplete underTest = SubscriptionComplete.of(UUID.randomUUID().toString(), dittoHeaders);
        final SubscriptionComplete deserialized = SubscriptionComplete.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }
}
