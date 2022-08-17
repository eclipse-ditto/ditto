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
package org.eclipse.ditto.connectivity.service.messaging.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@code SshTunnelState}.
 */
public class SshTunnelStateTest {

    private static final Connection CONNECTION_WITH_TUNNEL = TestConstants.createConnectionWithTunnel(true);
    private static final SshTunnelState ENABLED_TUNNEL = SshTunnelState.from(CONNECTION_WITH_TUNNEL);
    private static final int LOCAL_TUNNEL_PORT = 12345;
    private static final URI TUNNEL_URI = getTunnelUri(LOCAL_TUNNEL_PORT);

    private static final IllegalStateException TUNNEL_FAILED = new IllegalStateException("tunnel failed");

    @Test
    public void assertImmutability() {
        assertInstancesOf(SshTunnelState.class, areImmutable(), provided(Throwable.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SshTunnelState.class).verify();
    }


    @Test
    public void testFromConnectionNoTunnelConfigured() {
        final Connection connection = TestConstants.createConnection();
        final SshTunnelState tunnelState = SshTunnelState.from(connection);
        assertDisabled(tunnelState, connection);
    }

    @Test
    public void testFromConnectionWithEnabledTunnel() {
        final SshTunnelState tunnelState = SshTunnelState.from(CONNECTION_WITH_TUNNEL);
        assertClosed(tunnelState);
    }

    @Test
    public void testFromConnectionWithDisabledTunnel() {
        final Connection connectionWithTunnel = TestConstants.createConnectionWithTunnel(false);
        final SshTunnelState disabled = SshTunnelState.from(connectionWithTunnel);
        assertDisabled(disabled, connectionWithTunnel);
    }

    @Test
    public void establishedTunnel() {
        final SshTunnelState established = ENABLED_TUNNEL.established(LOCAL_TUNNEL_PORT);
        assertEstablished(established, LOCAL_TUNNEL_PORT);
    }

    @Test
    public void closedTunnel() {
        final SshTunnelState closed = ENABLED_TUNNEL.closed();
        assertClosed(closed);
    }

    @Test
    public void failedTunnel() {
        final SshTunnelState failed = ENABLED_TUNNEL.failed(TUNNEL_FAILED);
        assertFailed(failed);
    }

    @Test
    public void testTransitions() {
        final SshTunnelState established = ENABLED_TUNNEL.established(LOCAL_TUNNEL_PORT);
        assertEstablished(established, LOCAL_TUNNEL_PORT);
        final SshTunnelState closed = established.closed();
        assertClosed(closed);
        final SshTunnelState reEstablished = closed.established(LOCAL_TUNNEL_PORT + 1);
        assertEstablished(reEstablished, LOCAL_TUNNEL_PORT + 1);
        final SshTunnelState failed = reEstablished.failed(TUNNEL_FAILED);
        assertFailed(failed);
        final SshTunnelState reReEstablished = closed.established(LOCAL_TUNNEL_PORT + 2);
        assertEstablished(reReEstablished, LOCAL_TUNNEL_PORT + 2);
    }

    private void assertEstablished(final SshTunnelState established, final int localTunnelPort) {
        assertThat(established.isEnabled()).isTrue();
        assertThat(established.isEstablished()).isTrue();
        assertThat(established.isFailed()).isFalse();

        assertThat(established.getLocalPort()).isEqualTo(localTunnelPort);
        assertThat(established.getReason()).isNull();
        assertThat(established.getURI(CONNECTION_WITH_TUNNEL)).isEqualTo(getTunnelUri(localTunnelPort));
    }

    private void assertClosed(final SshTunnelState closed) {
        assertThat(closed.isEnabled()).isTrue();
        assertThat(closed.isEstablished()).isFalse();
        assertThat(closed.isFailed()).isFalse();

        assertThat(closed.getLocalPort()).isZero();
        assertThat(closed.getReason()).isNull();
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> closed.getURI(CONNECTION_WITH_TUNNEL));
    }

    private void assertFailed(final SshTunnelState failed) {
        assertThat(failed.isEnabled()).isTrue();
        assertThat(failed.isEstablished()).isFalse();
        assertThat(failed.isFailed()).isTrue();

        assertThat(failed.getLocalPort()).isZero();
        assertThat(failed.getReason()).isInstanceOf(IllegalStateException.class);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> failed.getURI(CONNECTION_WITH_TUNNEL));
    }

    private void assertDisabled(final SshTunnelState disabled, final Connection connection) {
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.isEstablished()).isFalse();
        assertThat(disabled.isFailed()).isFalse();

        assertThat(disabled.getLocalPort()).isZero();
        assertThat(disabled.getReason()).isNull();
        assertThat(disabled.getURI(connection)).isEqualTo(URI.create(connection.getUri()));
    }

    private static URI getTunnelUri(final int localTunnelPort) {
        return URI.create("amqps://username:password@localhost:" + localTunnelPort);
    }
}
