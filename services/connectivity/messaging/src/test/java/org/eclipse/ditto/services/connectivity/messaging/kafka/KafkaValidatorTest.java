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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Topic;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaValidator}.
 */
public final class KafkaValidatorTest {

    private static Map<String, String> defaultSpecificConfig = new HashMap<>();

    private KafkaValidator underTest;

    @BeforeClass
    public static void initTestFixture() {
        defaultSpecificConfig = new HashMap<>();
        defaultSpecificConfig.put("bootstrapServers", "localhost:1883");
    }

    @Before
    public void setUp() {
        underTest = KafkaValidator.getInstance();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(KafkaValidator.class, areImmutable());
    }

    @Test
    public void testSourcesAreInvalid() {
        final Source source = ConnectivityModelFactory.newSource(AUTHORIZATION_CONTEXT, "any");

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validateSource(source, DittoHeaders.empty(), () -> ""));
    }

    @Test
    public void testValidTargetAddress() {
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        underTest.validate(getConnectionWithTarget("events"), emptyDittoHeaders);
        underTest.validate(getConnectionWithTarget("ditto/{{thing:id}}"), emptyDittoHeaders);
        underTest.validate(getConnectionWithTarget("{{thing:namespace}}/{{thing:name}}"), emptyDittoHeaders);
        underTest.validate(getConnectionWithTarget("events#{{topic:full}}"), emptyDittoHeaders);
        underTest.validate(getConnectionWithTarget("ditto/{{header:x}}"), emptyDittoHeaders);
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
        underTest.validate(getConnectionWithBootstrapServers("foo:123"), emptyDittoHeaders);
        underTest.validate(getConnectionWithBootstrapServers("foo:123,bar:456"), emptyDittoHeaders);
        underTest.validate(getConnectionWithBootstrapServers("foo:123, bar:456 , baz:789"), emptyDittoHeaders);
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
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(target, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .specificConfig(defaultSpecificConfig)
                .build();
    }

    private static Connection getConnectionWithBootstrapServers(final String bootstrapServers) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("bootstrapServers", bootstrapServers);
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTarget("events", AUTHORIZATION_CONTEXT, null, 1,
                        Topic.LIVE_EVENTS)))
                .specificConfig(specificConfig)
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(connection, DittoHeaders.empty()));
    }

}
