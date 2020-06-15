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
package org.eclipse.ditto.services.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link StreamingSessionIdentifier}.
 */
public final class StreamingSessionIdentifierTest {

    private static final String KNOWN_STREAMING_SESSION_CLIENT_ID = "client";
    private static final String KNOWN_STREAMING_SESSION_SERVER_ID = "server";
    private static final String KNOWN_STREAMING_SESSION_IDENTIFIER =
            KNOWN_STREAMING_SESSION_CLIENT_ID + StreamingSessionIdentifier.DELIMITER +
                    KNOWN_STREAMING_SESSION_SERVER_ID;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StreamingSessionIdentifier.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamingSessionIdentifier.class, areImmutable());
    }

    @Test
    public void toStringReturnsExpected() {
        final StreamingSessionIdentifier underTest = StreamingSessionIdentifier.of(KNOWN_STREAMING_SESSION_IDENTIFIER);

        assertThat(underTest.toString()).isEqualTo(KNOWN_STREAMING_SESSION_IDENTIFIER);
        assertThat(underTest.getClientSessionId()).isEqualTo(KNOWN_STREAMING_SESSION_CLIENT_ID);
        assertThat(underTest.getServerSessionId()).isEqualTo(KNOWN_STREAMING_SESSION_SERVER_ID);
    }

    @Test
    public void toStringWithClientServerIdCreatorReturnsExpected() {
        final StreamingSessionIdentifier underTest = StreamingSessionIdentifier.of(KNOWN_STREAMING_SESSION_CLIENT_ID,
                KNOWN_STREAMING_SESSION_SERVER_ID);

        assertThat(underTest.toString()).isEqualTo(KNOWN_STREAMING_SESSION_IDENTIFIER);
        assertThat(underTest.getClientSessionId()).isEqualTo(KNOWN_STREAMING_SESSION_CLIENT_ID);
        assertThat(underTest.getServerSessionId()).isEqualTo(KNOWN_STREAMING_SESSION_SERVER_ID);
    }

    @Test
    public void lengthReturnsExpected() {
        final StreamingSessionIdentifier underTest = StreamingSessionIdentifier.of(KNOWN_STREAMING_SESSION_IDENTIFIER);

        assertThat(underTest.length()).isEqualTo(KNOWN_STREAMING_SESSION_IDENTIFIER.length());
    }

    @Test
    public void charAtReturnsExpected() {
        final byte charIndex = 3;
        final StreamingSessionIdentifier underTest = StreamingSessionIdentifier.of(KNOWN_STREAMING_SESSION_IDENTIFIER);

        assertThat(underTest.charAt(charIndex)).isEqualTo(KNOWN_STREAMING_SESSION_IDENTIFIER.charAt(charIndex));
    }

    @Test
    public void subSequenceReturnsExpected() {
        final byte sequenceStart = 5;
        final byte sequenceEnd = 11;
        final StreamingSessionIdentifier underTest = StreamingSessionIdentifier.of(KNOWN_STREAMING_SESSION_IDENTIFIER);

        assertThat(underTest.subSequence(sequenceStart, sequenceEnd))
                .isEqualTo(KNOWN_STREAMING_SESSION_IDENTIFIER.subSequence(sequenceStart, sequenceEnd));
    }

}