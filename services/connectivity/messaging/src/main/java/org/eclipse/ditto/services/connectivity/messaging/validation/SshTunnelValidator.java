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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.net.URI;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;
import org.eclipse.ditto.model.connectivity.SshPublicKeyAuthentication;
import org.eclipse.ditto.model.connectivity.SshTunnel;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.PublicKeyAuthenticationFactory;

/**
 * Validates the ssh tunnel configuration of a connection.
 */
public final class SshTunnelValidator {

    private static final String SSH_SCHEME = "ssh";
    private final DittoHeaders dittoHeaders;

    /**
     * Creates a new instance of SshTunnelValidator with the given headers.
     *
     * @param dittoHeaders the ditto headers
     * @return the new instance
     */
    public static SshTunnelValidator getInstance(final DittoHeaders dittoHeaders) {
        return new SshTunnelValidator(dittoHeaders);
    }

    private SshTunnelValidator(final DittoHeaders dittoHeaders) {
        this.dittoHeaders = dittoHeaders;
    }

    /**
     * Validates the ssh tunnel configuration of the connection.
     *
     * @param tunnel the tunnel to validate
     */
    void validate(final SshTunnel tunnel) {
        validateUri(tunnel.getUri());
        tunnel.getCredentials().accept(new SshTunnelCredentialsValidator());
        validateFingerprints(tunnel);
    }

    private void validateFingerprints(final SshTunnel tunnel) {
        if (tunnel.isValidateHost() && tunnel.getKnownHosts().isEmpty()) {
            throw configError("Specify at least one fingerprint if host validationis enabled.");
        }
    }

    private void validateUri(final String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            throw configError("The URI of the ssh server must not be empty.");
        }
        try {
            final URI uri = new URI(uriString).parseServerAuthority();
            if (!uri.getScheme().equals(SSH_SCHEME)) {
                throw configError("The scheme for the ssh endpoint must be 'ssh'.");
            }
        } catch (final Exception exception) {
            final String message = String.format("The SSH server URI '%s' is not valid: %s", uriString,
                    exception.getMessage());
            throw configError(message);
        }
    }

    private DittoRuntimeException configError(final String message) {

        return ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private final class SshTunnelCredentialsValidator implements CredentialsVisitor<Void> {

        @Override
        public Void clientCertificate(final ClientCertificateCredentials credentials) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "Credentials type " + ClientCertificateCredentials.TYPE + " cannot be used for an ssh tunnel.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        @Override
        public Void usernamePassword(final UserPasswordCredentials credentials) {
            if (credentials.getUsername().isEmpty()) {
                throw configError("SSH tunnel username must not be empty.");
            }
            if (credentials.getPassword().isEmpty()) {
                throw configError("SSH tunnel password must not be empty.");
            }
            return null;
        }

        @Override
        public Void sshPublicKeyAuthentication(final SshPublicKeyAuthentication credentials) {
            credentials.accept(PublicKeyAuthenticationFactory.getInstance(dittoHeaders));
            return null;
        }
    }
}
