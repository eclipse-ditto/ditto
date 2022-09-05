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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;
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
 * Unit test for {@link KafkaValidator}.
 */
public final class KafkaValidatorTest {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static Map<String, String> defaultSpecificConfig = new HashMap<>();
    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;

    private KafkaValidator underTest;

    @BeforeClass
    public static void initTestFixture() {
        defaultSpecificConfig = new HashMap<>();
        defaultSpecificConfig.put("bootstrapServers", "localhost:1883");
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
        underTest = KafkaValidator.getInstance();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(KafkaValidator.class, areImmutable(), provided(KafkaSpecificConfig.class).isAlsoImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithSource("events"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithSource("events.with.dots"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithSource("events_with_underscores"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithSource("events-with-dashes"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("events/"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto#"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto#notANumber"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto*a"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto\\"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto/{{thing:id}}"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(
                getConnectionWithSource("{{thing:namespace}}/{{thing:name}}"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("events#{{topic:full}}"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithSource("ditto/{{header:x}}"));
    }

    @Test
    public void testInvalidSourceQos() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                        .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                                .address("events")
                                .authorizationContext(AUTHORIZATION_CONTEXT)
                                .qos(2)
                                .build()))
                        .specificConfig(defaultSpecificConfig)
                        .build());
    }

    @Test
    public void testValidTargetAddress() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithTarget("events"), emptyDittoHeaders, actorSystem, connectivityConfig);
        underTest.validate(getConnectionWithTarget("ditto/{{thing:id}}"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithTarget("{{thing:namespace}}/{{thing:name}}"), emptyDittoHeaders,
                actorSystem, connectivityConfig);
        underTest.validate(getConnectionWithTarget("events#{{topic:full}}"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithTarget("ditto/{{header:x}}"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget("events/"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget("ditto#"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget("ditto#notANumber"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget("ditto*a"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithTarget("ditto\\"));
    }

    @Test
    public void testValidBootstrapServers() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithBootstrapServers("foo:123"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithBootstrapServers("foo:123,bar:456"), emptyDittoHeaders, actorSystem,
                connectivityConfig);
        underTest.validate(getConnectionWithBootstrapServers("foo:123, bar:456 , baz:789"), emptyDittoHeaders,
                actorSystem, connectivityConfig);
    }

    @Test
    public void testInvalidBootstrapServers() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithBootstrapServers(null));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithBootstrapServers(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithBootstrapServers("fo#add#123o:123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithBootstrapServers("foo:123;bar:456"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(getConnectionWithBootstrapServers("http://foo:123"));
    }

    private static Connection getConnectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                        ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(target)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .specificConfig(defaultSpecificConfig)
                .build();
    }

    private static Connection getConnectionWithSource(final String sourceAddress) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                        ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                        .address(sourceAddress)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .build()))
                .specificConfig(defaultSpecificConfig)
                .build();
    }

    private static Connection getConnectionWithBootstrapServers(final String bootstrapServers) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("bootstrapServers", bootstrapServers);
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                        ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address("events")
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .specificConfig(specificConfig)
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(
                        () -> underTest.validate(connection, DittoHeaders.empty(), actorSystem, connectivityConfig));
    }

}
