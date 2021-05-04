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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.SshTunnel;

/**
 * Holds the current state of the SSH tunnel (enabled, established, failed, ...)
 */
public final class SshTunnelState {

    private static final String LOCALHOST = "localhost";

    private final boolean enabled;
    private final int port;
    private final Throwable reason;

    private SshTunnelState(final boolean enabled, final int port, @Nullable final Throwable reason) {
        this.enabled = enabled;
        this.port = port;
        this.reason = reason;
    }

    /**
     * Build tunnel state from a given connection.
     *
     * @param connection the connection
     * @return the tunnel state built from the connection
     */
    public static SshTunnelState from(final Connection connection) {
        final boolean enabled = isSshTunnelEnabled(connection);
        return new SshTunnelState(enabled, 0, null);
    }

    /**
     * @return enabled ssh tunnel state
     */
    public static SshTunnelState enabled() {
        return new SshTunnelState(true, 0, null);
    }

    /**
     * @return disabled ssh tunnel state
     */
    public static SshTunnelState disabled() {
        return new SshTunnelState(false, 0, null);
    }

    /**
     * @return whether tunnel is enabled according to the connection configuration
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return whether the tunnel has failed
     */
    public boolean isFailed() {
        return reason != null;
    }

    /**
     * @return whether the tunnel is actually established
     */
    public boolean isEstablished() {
        return port > 0;
    }

    /**
     * @return the local ssh tunnel port
     */
    public int getLocalPort() {
        return port;
    }

    /**
     * Gets reason.
     *
     * @return the reason
     */
    @Nullable
    public Throwable getReason() {
        return reason;
    }

    /**
     * Changes the state to established.
     *
     * @return new established tunnel state
     */
    public SshTunnelState established(final int establishedPort) {
        return new SshTunnelState(enabled, establishedPort, null);
    }

    /**
     * Changes the state to closed.
     *
     * @return new closed tunnel state
     */
    public SshTunnelState closed() {
        return new SshTunnelState(enabled, 0, null);
    }

    /**
     * Changes the state to failed with the given reason.
     *
     * @param failedReason the reason why the tunnel failed
     * @return new failed tunnel state
     */
    public SshTunnelState failed(final Throwable failedReason) {
        return new SshTunnelState(enabled, 0, failedReason);
    }

    /**
     * Returns the connection URI or the local SSH tunnel endpoint if tunnel is enabled for the connection.
     *
     * @param connection the connection
     * @return the uri a client connects to
     * @throws IllegalStateException if the tunnel is enabled but the tunnel is not (yet) established
     */
    public URI getURI(final Connection connection) {
        if (isSshTunnelEnabled(connection)) {
            if (port > 0) {
                final URI originalURI = URI.create(connection.getUri());
                try {
                    return new URI(originalURI.getScheme(), originalURI.getUserInfo(), LOCALHOST, port,
                            originalURI.getPath(), originalURI.getQuery(), originalURI.getFragment());
                } catch (final URISyntaxException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            } else {
                final String message = String.format(
                        "Connection '%s' is configured to connect via SSH tunnel, but local tunnel is not available.",
                        connection.getId());
                throw new IllegalStateException(message);
            }
        } else {
            return URI.create(connection.getUri());
        }
    }

    private static Boolean isSshTunnelEnabled(final Connection connection) {
        return connection.getSshTunnel().map(SshTunnel::isEnabled).orElse(false);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SshTunnelState that = (SshTunnelState) o;
        return enabled == that.enabled && port == that.port && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, port, reason);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", port=" + port +
                ", reason=" + reason +
                "]";
    }
}
