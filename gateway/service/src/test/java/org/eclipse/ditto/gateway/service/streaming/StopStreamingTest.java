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
package org.eclipse.ditto.gateway.service.streaming;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link StopStreaming}.
 */
public final class StopStreamingTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StopStreaming.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StopStreaming.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullStreamingType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StopStreaming(null, "my-correlation-id"))
                .withMessage("The streamingType must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullCorrelationId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StopStreaming(StreamingType.LIVE_COMMANDS, null))
                .withMessage("The connectionCorrelationId must not be null!")
                .withNoCause();
    }

    @Test
    public void gettersReturnExpected() {
        final StreamingType streamingType = StreamingType.EVENTS;
        final String connectionCorrelationId = "my-correlation-id";

        final StopStreaming underTest = new StopStreaming(streamingType, connectionCorrelationId);

        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(underTest.getStreamingType()).isEqualTo(streamingType);
        softly.assertThat(underTest.getConnectionCorrelationId()).isEqualTo(connectionCorrelationId);
        softly.assertAll();
    }

}
