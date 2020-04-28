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
package org.eclipse.ditto.signals.events.thingsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SubscriptionFailed}.
 */
public final class SubscriptionFailedTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubscriptionFailed.class, areImmutable(),
                provided(DittoRuntimeException.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscriptionFailed.class).withRedefinedSuperclass().verify();
    }

    @Test
    public void serialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final DittoRuntimeException error =
                DittoRuntimeException.fromUnknownErrorJson(
                        InvalidRqlExpressionException.newBuilder().build().toJson(), dittoHeaders)
                        .orElseThrow(NoSuchElementException::new);
        final SubscriptionFailed underTest = SubscriptionFailed.of(UUID.randomUUID().toString(), error, dittoHeaders);
        final SubscriptionFailed deserialized = SubscriptionFailed.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }
}
