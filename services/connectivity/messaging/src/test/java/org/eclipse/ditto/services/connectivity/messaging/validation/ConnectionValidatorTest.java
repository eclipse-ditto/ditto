/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpValidator;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator}.
 */
public class ConnectionValidatorTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionValidator.class,
                areImmutable(),
                // mutability-detector cannot detect that maps built from stream collectors are safely copied.
                assumingFields("specMap").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void acceptValidConnection() {
        final ActorSystem system = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test"));
        final Connection connection = TestConstants.createConnection("connectionId", system);
        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        underTest.validate(connection, DittoHeaders.empty());
    }

    @Test
    public void rejectConnectionWithSourceWithoutAddresses() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder("id", ConnectionType.AMQP_10, ConnectionStatus.OPEN,
                        "amqp://localhost:5671")
                        .sources(Collections.singletonList(
                                ConnectivityModelFactory.newSource(1, 0,
                                        TestConstants.Authorization.AUTHORIZATION_CONTEXT)))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectConnectionWithEmptySourceAddress() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder("id", ConnectionType.AMQP_10, ConnectionStatus.OPEN,
                        "amqp://localhost:5671")
                        .sources(Collections.singletonList(
                                ConnectivityModelFactory.newSource(1, 0,
                                        TestConstants.Authorization.AUTHORIZATION_CONTEXT,
                                        "sourceAddress1", "")))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void rejectConnectionWithEmptyTargetAddress() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder("id", ConnectionType.AMQP_10, ConnectionStatus.OPEN,
                        "amqp://localhost:5671")
                        .targets(Collections.singleton(
                                ConnectivityModelFactory.newTarget("",
                                        TestConstants.Authorization.AUTHORIZATION_CONTEXT,
                                        Topic.LIVE_MESSAGES)))
                        .build();

        final ConnectionValidator underTest = ConnectionValidator.of(AmqpValidator.newInstance());
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

}
