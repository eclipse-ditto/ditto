/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.createConnection;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpValidator;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator}.
 */
public class ConnectionValidatorTest {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionValidator.class,
                areImmutable(),
                // mutability-detector cannot detect that maps built from stream collectors are safely copied.
                assumingFields("specMap").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                provided(QueryFilterCriteriaFactory.class).isAlsoImmutable());
    }

    @Test
    public void acceptValidConnection() {
        final Connection connection = createConnection(CONNECTION_ID);
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        underTest.validate(connection, DittoHeaders.empty());
    }

    @Test
    public void rejectConnectionWithSourceWithoutAddresses() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_10, ConnectivityStatus.OPEN, "amqp://localhost:5671")
                        .sources(singletonList(
                                ConnectivityModelFactory.newSourceBuilder()
                                        .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                                        .consumerCount(0)
                                        .index(1)
                                        .build()))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectConnectionWithEmptySourceAddress() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_10, ConnectivityStatus.OPEN, "amqp://localhost:5671")
                        .sources(singletonList(
                                ConnectivityModelFactory.newSourceBuilder()
                                        .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                                        .address("")
                                        .consumerCount(1)
                                        .index(0)
                                        .build()))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectConnectionWithEmptyTargetAddress() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_10, ConnectivityStatus.OPEN, "amqp://localhost:5671")
                        .targets(Collections.singletonList(
                                ConnectivityModelFactory.newTarget("",
                                        Authorization.AUTHORIZATION_CONTEXT,
                                        null, null,
                                        Topic.LIVE_MESSAGES)))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectConnectionWithIllFormedTrustedCertificates() {
        final Connection connection = createConnection(CONNECTION_ID).toBuilder()
                .trustedCertificates("Wurst")
                .build();
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void acceptConnectionWithTrustedCertificates() {
        final String trustedCertificates = String.join("\n",
                Certificates.CA_CRT,
                Certificates.SERVER_CRT,
                Certificates.CLIENT_CRT,
                Certificates.CLIENT_SELF_SIGNED_CRT);
        final Connection connection = createConnection(CONNECTION_ID).toBuilder()
                .trustedCertificates(trustedCertificates)
                .build();
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        underTest.validate(connection, DittoHeaders.empty());
    }

    @Test
    public void rejectIllFormedClientCertificate() {
        final Connection connection = createConnection(CONNECTION_ID).toBuilder()
                .credentials(ClientCertificateCredentials.newBuilder()
                        .clientKey(Certificates.CLIENT_KEY)
                        .clientCertificate("Wurst")
                        .build())
                .build();
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectIllFormedClientKey() {
        final Connection connection = createConnection(CONNECTION_ID).toBuilder()
                .credentials(ClientCertificateCredentials.newBuilder()
                        .clientKey("-----BEGIN RSA PRIVATE KEY-----\nWurst\n-----END RSA PRIVATE KEY-----")
                        .clientCertificate(Certificates.CLIENT_CRT)
                        .build())
                .build();
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));

    }

    @Test
    public void acceptClientCertificate() {
        final Connection connection = createConnection(CONNECTION_ID).toBuilder()
                .credentials(ClientCertificateCredentials.newBuilder()
                        .clientKey(Certificates.CLIENT_KEY)
                        .clientCertificate(Certificates.CLIENT_CRT)
                        .build())
                .build();
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        underTest.validate(connection, DittoHeaders.empty());
    }

}
