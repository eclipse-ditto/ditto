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
package org.eclipse.ditto.connectivity.model;

import java.util.List;

/**
 * A mutable builder for a {@link SshTunnelBuilder} with a fluent API.
 *
 * @since 2.0.0
 */
public interface SshTunnelBuilder {

    /**
     * Sets the activation of an {@code SshTunnel}.
     *
     * @param enabled whether the ssh tunnel is enabled.
     * @return this builder to allow method chaining.
     */
    SshTunnelBuilder enabled(boolean enabled);

    /**
     * Sets the credentials for {@code SshTunnel}.
     *
     * @param credentials the credentials.
     * @return this builder.
     * @throws NullPointerException if {@code credentials} is {@code null}.
     */
    SshTunnelBuilder credentials(Credentials credentials);

    /**
     * Sets a list of known hosts for {@code SshTunnel}.
     *
     * @param knownHosts the known hosts
     * @return this builder
     * @throws NullPointerException if {@code knownHosts} is {@code null}.
     */
    SshTunnelBuilder knownHosts(List<String> knownHosts);

    /**
     * Enables/disables host validation.
     *
     * @param validateHost {@code true} if host validation is enabled
     * @return this builder
     */
    SshTunnelBuilder validateHost(boolean validateHost);

    /**
     * Sets the URI to use in the {@code SshTunnel}.
     *
     * @param uri the URI.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code uri} is {@code null}.
     */
    SshTunnelBuilder uri(String uri);

    /**
     * Builds a new {@link SshTunnel}.
     *
     * @return the new {@link SshTunnel}
     */
    SshTunnel build();
}
