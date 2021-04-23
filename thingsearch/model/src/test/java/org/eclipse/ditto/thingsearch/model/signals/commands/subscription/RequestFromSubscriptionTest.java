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
package org.eclipse.ditto.thingsearch.model.signals.commands.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link RequestFromSubscription}.
 */
public final class RequestFromSubscriptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(RequestFromSubscription.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RequestFromSubscription.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void serialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final RequestFromSubscription
                underTest = RequestFromSubscription.of(UUID.randomUUID().toString(), 9L, dittoHeaders);
        final RequestFromSubscription deserialized = RequestFromSubscription.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }

}
