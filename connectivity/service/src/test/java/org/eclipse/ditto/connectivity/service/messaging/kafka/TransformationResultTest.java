/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class TransformationResultTest {

    private static final Map<String, String> EXPIRED_HEADERS = Map.of("creation-time", "0", "ttl", "1000");
    private static final Map<String, String> NOT_EXPIRED_HEADERS =
            Map.of("creation-time", String.valueOf(Instant.now().toEpochMilli()), "ttl", "100000");

    @Test
    public void assertImmutability() {
        assertInstancesOf(TransformationResult.class, areImmutable(),
                provided(DittoRuntimeException.class, ExternalMessage.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TransformationResult.class).verify();
    }

    @Test
    public void failedResultWithoutHeadersIsNotExpired() {
        final TransformationResult failed = TransformationResult.failed(MessageRejectedException.getInstance());
        assertThat(failed.isExpired()).isFalse();
    }

    @Test
    public void failedResultWithExpiredHeadersIsExpired() {
        final TransformationResult failed = TransformationResult.failed(MessageRejectedException.getInstance()
                .setDittoHeaders(DittoHeaders.of(EXPIRED_HEADERS)));
        assertThat(failed.isExpired()).isTrue();
    }

    @Test
    public void failedResultWithNonExpiredHeadersIsExpired() {
        final TransformationResult failed = TransformationResult.failed(MessageRejectedException.getInstance()
                .setDittoHeaders(DittoHeaders.of(NOT_EXPIRED_HEADERS)));
        assertThat(failed.isExpired()).isFalse();
    }

    @Test
    public void successfulResultWithoutHeadersIsNotExpired() {
        final ExternalMessage externalMessage = mock(ExternalMessage.class);
        when(externalMessage.getHeaders()).thenReturn(Map.of());
        final TransformationResult failed = TransformationResult.successful(externalMessage);
        assertThat(failed.isExpired()).isFalse();
    }

    @Test
    public void successfulResultWithExpiredHeadersIsExpired() {
        final ExternalMessage externalMessage = mock(ExternalMessage.class);
        when(externalMessage.getHeaders()).thenReturn(EXPIRED_HEADERS);
        final TransformationResult failed = TransformationResult.successful(externalMessage);
        assertThat(failed.isExpired()).isTrue();
    }

    @Test
    public void successfulResultWithNonExpiredHeadersIsExpired() {
        final ExternalMessage externalMessage = mock(ExternalMessage.class);
        when(externalMessage.getHeaders()).thenReturn(NOT_EXPIRED_HEADERS);
        final TransformationResult failed = TransformationResult.successful(externalMessage);
        assertThat(failed.isExpired()).isFalse();
    }

}
