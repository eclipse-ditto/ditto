/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Topic;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaValidator}.
 */
public class KafkaValidatorTest {

    private static final Map<String, String> DEFAULT_SPECIFIC_CONFIG = new HashMap<>();

    static {
        DEFAULT_SPECIFIC_CONFIG.put("bootstrapServers", "localhost:1883");
    }

    @Test
    public void testSourcesAreInvalid() {
        verifyConnectionConfigurationInvalidExceptionIsThrownForSource(source());
    }

    private Source source() {
        return ConnectivityModelFactory.newSource(AUTHORIZATION_CONTEXT, "any");
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrownForSource(final Source source) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> KafkaValidator.newInstance().validateSource(source, DittoHeaders.empty(), () -> ""));
    }

    @Test
    public void testValidTargetAddress() {
        KafkaValidator.newInstance().validate(connectionWithTarget("events"), DittoHeaders.empty());
        KafkaValidator.newInstance().validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty());
        KafkaValidator.newInstance().validate(connectionWithTarget("{{thing:namespace}}/{{thing:name}}"), DittoHeaders.empty());
        KafkaValidator.newInstance().validate(connectionWithTarget("events#{{topic:full}}"), DittoHeaders.empty());
        KafkaValidator.newInstance().validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("events/"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto#"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto#notANumber"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto*a"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto\\"));
    }

    @Test
    public void testValidBootstrapServers() {
        KafkaValidator.newInstance().validate(connectionWithBootstrapServers("foo:123"), DittoHeaders.empty());
        KafkaValidator.newInstance().validate(connectionWithBootstrapServers("foo:123,bar:456"), DittoHeaders.empty());
        KafkaValidator.newInstance()
                .validate(connectionWithBootstrapServers("foo:123, bar:456 , baz:789"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidBootstrapServers() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithBootstrapServers(null));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithBootstrapServers(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithBootstrapServers("fo#add#123o:123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithBootstrapServers("foo:123;bar:456"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithBootstrapServers("http://foo:123"));
    }

    private Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(target, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .specificConfig(DEFAULT_SPECIFIC_CONFIG)
                .build();
    }

    private Connection connectionWithBootstrapServers(final String bootstrapServers) {
        final Map<String, String> specificConfig = new HashMap<>();
        specificConfig.put("bootstrapServers", bootstrapServers);
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget("events", AUTHORIZATION_CONTEXT, null, 1,
                                Topic.LIVE_EVENTS)))
                .specificConfig(specificConfig)
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> KafkaValidator.newInstance().validate(connection, DittoHeaders.empty()));
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(KafkaValidator.class, areImmutable());
    }

}