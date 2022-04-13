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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import static java.util.Collections.singletonList;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newSourceAddressEnforcement;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssertAlternative;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Base class for MqttValidator tests.
 */
abstract class AbstractMqttValidatorTest {

    protected static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    protected static ActorSystem actorSystem;
    protected static ConnectivityConfig connectivityConfig;

    @BeforeClass
    public static void setUp() {
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

    void testInvalidSourceEnforcementFilters(final Source... sources) {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(Arrays.asList(sources))
                .build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    protected abstract ThrowableAssertAlternative<ConnectionConfigurationInvalidException>
    verifyConnectionConfigurationInvalidExceptionIsThrown(Connection connection);

    Connection connectionWithSource(final String source) {
        final Enforcement enforcement = newSourceAddressEnforcement("things/{{ thing:id }}", "things/{{ entity:id }}");
        final Source mqttSource = ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .address(source)
                .qos(1)
                .build();

        return connectionWithSource(mqttSource);
    }

    Connection connectionWithSource(final Source mqttSource) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(mqttSource))
                .build();
    }

    Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(target)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .build();
    }

}
