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
package org.eclipse.ditto.services.connectivity.messaging.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates.SERVER_PUBKEY;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates.SERVER_PUBKEY_FINGERPRINT_MD5;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates.SERVER_PUBKEY_FINGERPRINT_SHA256;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates.SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FingerprintVerifierTest {

    private ClientSession mockSession;
    private SocketAddress mockAddress;

    @Before
    public void setUp() throws Exception {
        mockSession = Mockito.mock(ClientSession.class);
        mockAddress = Mockito.mock(SocketAddress.class);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(FingerprintVerifier.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(FingerprintVerifier.class).usingGetClass().verify();
    }

    @Test
    public void verifyServerKeySHA256() {
        final ServerKeyVerifier underTest = new FingerprintVerifier(List.of(SERVER_PUBKEY_FINGERPRINT_SHA256));
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeySHA256Lowercase() {
        final List<String> knownHosts = List.of("sha256:" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX.toLowerCase());
        final ServerKeyVerifier underTest = new FingerprintVerifier(knownHosts);
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeySHA256Uppercase() {
        final List<String> knownHosts = List.of("sha256:" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX.toUpperCase());
        final ServerKeyVerifier underTest = new FingerprintVerifier(knownHosts);
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeySHA_256() {
        final List<String> knownHosts = List.of("SHA-256:" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX);
        final ServerKeyVerifier underTest = new FingerprintVerifier(knownHosts);
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeyMD5() {
        final ServerKeyVerifier underTest = new FingerprintVerifier(List.of(SERVER_PUBKEY_FINGERPRINT_MD5));
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeyDefault() {
        final ServerKeyVerifier underTest = new FingerprintVerifier(List.of(SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX));
        assertThat(underTest.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isTrue();
    }

    @Test
    public void verifyServerKeyFailsForEmptyList() {
        final ServerKeyVerifier fingerPrintVerifier = new FingerprintVerifier(Collections.emptyList());
        assertThat(fingerPrintVerifier.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isFalse();
    }

    @Test
    public void verifyServerKeyFailsForWrongAlgorithm() {
        final List<String> knownHosts = List.of("SHA512:" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX);
        final ServerKeyVerifier fingerPrintVerifier = new FingerprintVerifier(knownHosts);
        assertThat(fingerPrintVerifier.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isFalse();
    }

    @Test
    public void verifyServerKeyFailsForUnknownAlgorithm() {
        final List<String> knownHosts = List.of("MURMUR:" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX);
        final ServerKeyVerifier fingerPrintVerifier = new FingerprintVerifier(knownHosts);
        assertThat(fingerPrintVerifier.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isFalse();
    }

    @Test
    public void verifyServerKeyFailsForEmptyAlgorithm() {
        final List<String> knownHosts = List.of(":" + SERVER_PUBKEY_FINGERPRINT_SHA256_NOPREFIX);
        final ServerKeyVerifier fingerPrintVerifier = new FingerprintVerifier(knownHosts);
        assertThat(fingerPrintVerifier.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isFalse();
    }

    @Test
    public void verifyServerKeyFailsForWrongFingerprint() {
        final List<String> knownHosts = List.of("MD5:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00");
        final ServerKeyVerifier fingerPrintVerifier = new FingerprintVerifier(knownHosts);
        assertThat(fingerPrintVerifier.verifyServerKey(mockSession, mockAddress, SERVER_PUBKEY)).isFalse();
    }
}
