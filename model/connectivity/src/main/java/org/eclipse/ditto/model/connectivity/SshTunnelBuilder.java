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
package org.eclipse.ditto.model.connectivity;

import java.util.List;

/**
 * A mutable builder for a {@link SshTunnelBuilder} with a fluent API.
 */
public interface SshTunnelBuilder {

    /**
     * Sets the activation of an {@code SshTunnel}.
     *
     * @param activate sets if the ssh tunnel is active.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code id} is {@code null}.
     */
    SshTunnelBuilder enabled(Boolean activate);

    /**
     * Sets the credentials for {@code SshTunnel}.
     *
     * @param credentials the credentials.
     * @return this builder.
     */
    SshTunnelBuilder credentials(Credentials credentials);

    /**
     * Sets a list of known hosts for {@code SshTunnel}.
     *
     * @param knownHosts the known hosts
     * @return this builder
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
     * Builds a new {@link org.eclipse.ditto.model.connectivity.SshTunnel}.
     *
     * @return the new {@link SshTunnel}
     */
    SshTunnel build();
}
