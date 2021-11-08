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

import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
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
    private final HostValidator hostValidator;

    private static final String ALLOWED_CHARACTERS = "\\x21\\x23-\\x5B\\x5D-\\x7E";
    private static final Pattern REQUESTED_SCOPES_REGEX =
            Pattern.compile("^[" + ALLOWED_CHARACTERS + "]+( [" + ALLOWED_CHARACTERS + "]+)*$");

    private CredentialsValidationVisitor(final Connection connection, final DittoHeaders dittoHeaders,
            final ConnectivityConfig config, final HostValidator hostValidator) {
        this.connection = connection;
        this.dittoHeaders = dittoHeaders;
        algorithms = config.getConnectionConfig().getHttpPushConfig().getHmacAlgorithms().keySet();
        this.hostValidator = hostValidator;
    }

    static CredentialsValidationVisitor of(final Connection connection, final DittoHeaders dittoHeaders,
            final ConnectivityConfig connectivityConfig, final HostValidator hostValidator) {
        return new CredentialsValidationVisitor(connection, dittoHeaders, connectivityConfig, hostValidator);
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
        switch (connection.getConnectionType()) {
            case AMQP_10:
            case HTTP_PUSH:
                break;
            default:
                throw ConnectionConfigurationInvalidException.newBuilder(
                        "HMAC credentials are not supported for the connection type.")
                        .description("Only HTTP and AMQP 1.0 connections support HMAC credentials.")
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

    @Override
    public Void oauthClientCredentials(final OAuthClientCredentials credentials) {
        if (ConnectionType.HTTP_PUSH != connection.getConnectionType()) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                            "OAuth client credentials are only supported for HTTP connection type.")
                    .description("Only HTTP connections support OAuth client credentials.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        validateTokenEndpoint(credentials.getTokenEndpoint());
        if (!REQUESTED_SCOPES_REGEX.matcher(credentials.getRequestedScopes()).matches()) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                            "Invalid format of requested scopes: " + credentials.getRequestedScopes())
                    .description("Provide scopes as space separated list (RFC6749 section 3.3).")
                    .href("https://datatracker.ietf.org/doc/html/rfc6749#section-3.3")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return null;
    }

    private void validateTokenEndpoint(final String tokenEndpoint) {
        final URL url;
        try {
            url = new URL(tokenEndpoint);
        } catch (final Exception e) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                            String.format("Invalid token endpoint '%s' provided: %s", tokenEndpoint, e.getMessage()))
                    .description("Provide a valid URL as token endpoint.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        hostValidator.validateHostname(url.getHost(), dittoHeaders);
    }
}
