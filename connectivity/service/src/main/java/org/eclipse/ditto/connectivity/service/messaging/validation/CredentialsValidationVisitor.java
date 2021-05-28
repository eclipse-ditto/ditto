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

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

import akka.http.javadsl.model.Uri;

/**
 * Validate credentials in a connection.
 */
@Immutable
final class CredentialsValidationVisitor implements CredentialsVisitor<Void> {

    private final Connection connection;
    private final DittoHeaders dittoHeaders;
    private final Set<String> algorithms;

    private CredentialsValidationVisitor(final Connection connection, final DittoHeaders dittoHeaders,
            final ConnectivityConfig config) {
        this.connection = connection;
        this.dittoHeaders = dittoHeaders;
        algorithms = config.getConnectionConfig().getHttpPushConfig().getHmacAlgorithms().keySet();
    }

    static CredentialsValidationVisitor of(final Connection connection, final DittoHeaders dittoHeaders,
            final ConnectivityConfig connectivityConfig) {
        return new CredentialsValidationVisitor(connection, dittoHeaders, connectivityConfig);
    }

    @Override
    public Void clientCertificate(final ClientCertificateCredentials credentials) {
        return null;
    }

    @Override
    public Void usernamePassword(final UserPasswordCredentials credentials) {
        return null;
    }

    @Override
    public Void sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return null;
    }

    @Override
    public Void hmac(final HmacCredentials credentials) {
        if (connection.getConnectionType() != ConnectionType.HTTP_PUSH) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "HMAC credentials are not supported for the connection type.")
                    .description("Only http-push connections support HMAC credentials.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        final Uri uri = Uri.create(connection.getUri());
        if (!uri.getUserInfo().isEmpty()) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "There are conflicting authentication mechanisms.")
                    .description("HMAC credentials and URI userinfo may not be present at the same time.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        if (!algorithms.contains(credentials.getAlgorithm())) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "Unsupported HMAC algorithm: " + credentials.getAlgorithm())
                    .description("Supported algorithms: " + String.join(", ", algorithms))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return null;
    }
}
