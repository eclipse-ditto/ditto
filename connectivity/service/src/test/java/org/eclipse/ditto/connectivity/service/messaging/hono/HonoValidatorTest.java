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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.messaging.hono.HonoValidatorTest}.
 */
public final class HonoValidatorTest {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;

    private HonoValidator underTest;

    @BeforeClass
    public static void initTestFixture() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connectivityConfig = TestConstants.CONNECTIVITY_CONFIG;
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Before
    public void setUp() {
        underTest = HonoValidator.getInstance();
    }

    @Test
    public void testValidSourceAddress() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithSource("event"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithSource("telemetry"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithSource("command_response"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithSource(""), "empty");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithSource("command"),
                "[command_response, telemetry, event]");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithSource("events/"),
                "[command_response, telemetry, event]");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithSource("hono.telemetry" +
                ".c4bc9a62-8516-4232-bb81-dbbfe4d0fa8c_hub"), "[command_response, telemetry, event]");
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto*a"));

    }

    @Test
    public void testInvalidSourceQos() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.HONO,
                                ConnectivityStatus.OPEN, "tcp://localhost:999999")
                        .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                                .address("event")
                                .authorizationContext(AUTHORIZATION_CONTEXT)
                                .qos(3)
                                .build()))
                        .build());
    }

    @Test
    public void testValidTargetAddress() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithTarget("command"), emptyDittoHeaders, actorSystem, connectivityConfig);
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithTarget(""), "empty");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithTarget("event"), "command");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithTarget("telemtry"), "command");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(getConnectionWithTarget("command_response"),
                "command");
        verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(
                getConnectionWithTarget("hono.command.c4bc9a62-8516-4232-bb81-dbbfe4d0fa8c_hub/{{thing:id}}"),
                "command");
    }

    private static Connection getConnectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.HONO,
                        ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(target)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .build();
    }

    private static Connection getConnectionWithSource(final String sourceAddress) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.HONO,
                        ConnectivityStatus.OPEN, "tcp://localhost:99999")
                .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                        .address(sourceAddress)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .build()))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(
                        () -> underTest.validate(connection, DittoHeaders.empty(), actorSystem, connectivityConfig));
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrownAndMessage(final Connection connection,
            String messages) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(
                        () -> underTest.validate(connection, DittoHeaders.empty(), actorSystem, connectivityConfig))
                .withMessageContaining(messages);
    }

}

