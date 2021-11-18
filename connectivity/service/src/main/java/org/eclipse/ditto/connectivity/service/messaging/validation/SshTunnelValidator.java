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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.net.URI;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.SshTunnel;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.PublicKeyAuthenticationFactory;

/**
 * Validates the ssh tunnel configuration of a connection.
 */
final class SshTunnelValidator {

    private static final String SSH_SCHEME = "ssh";
    private final DittoHeaders dittoHeaders;
    private final HostValidator hostValidator;

    /**
     * Creates a new instance of SshTunnelValidator with the given headers.
     *
     * @param dittoHeaders the ditto headers
     * @param hostValidator the host validator to check the configured ssh host
     * @return the new instance
     */
    public static SshTunnelValidator getInstance(final DittoHeaders dittoHeaders,
            final HostValidator hostValidator) {
        return new SshTunnelValidator(dittoHeaders, hostValidator);
    }

    private SshTunnelValidator(final DittoHeaders dittoHeaders,
            final HostValidator hostValidator) {
        this.dittoHeaders = dittoHeaders;
        this.hostValidator = hostValidator;
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
            hostValidator.validateHostname(uri.getHost(), dittoHeaders);
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
            throw notSupportedException(ClientCertificateCredentials.TYPE);
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
        public Void sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
            credentials.accept(PublicKeyAuthenticationFactory.getInstance(dittoHeaders));
            return null;
        }

        @Override
        public Void hmac(final HmacCredentials credentials) {
            throw notSupportedException(HmacCredentials.TYPE);
        }

        @Override
        public Void oauthClientCredentials(final OAuthClientCredentials credentials) {
            throw notSupportedException(OAuthClientCredentials.TYPE);
        }

        private DittoRuntimeException notSupportedException(final String type) {
            return ConnectionConfigurationInvalidException.newBuilder(
                            "Credentials type " + type + " cannot be used for an ssh tunnel.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
