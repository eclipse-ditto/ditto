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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import static java.util.Collections.singletonList;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link RabbitMQValidator}.
 */
public final class RabbitMQValidatorTest {

    private static final RabbitMQValidator UNDER_TEST = RabbitMQValidator.newInstance();
    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;

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

    @Test
    public void testImmutability() {
        assertInstancesOf(RabbitMQValidator.class, areImmutable());
    }

    @Test
    public void testValidationOfEnforcementWithThingIdFilter() {
        testValidationOfEnforcement("thing");
    }

    @Test
    public void testValidationOfEnforcementWithEntityIdFilter() {
        testValidationOfEnforcement("entity");
    }

    @Test
    public void testValidationOfEnforcementWithPolicyIdFilter() {
        testValidationOfEnforcement("entity");
    }

    private void testValidationOfEnforcement(final String filterPrefix) {
        final Source source = newSourceBuilder()
                .enforcement(ConnectivityModelFactory.newEnforcement(
                        "{{ header:device_id }}",
                        "{{ " + filterPrefix + ":id }}",
                        "{{ " + filterPrefix + ":name }}", "{{ " + filterPrefix + ":namespace }}"))
                .build();

        UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource");
    }

    @Test
    public void testValidHeaderMapping() {
        final Source source = newSourceBuilder()
                .headerMapping(TestConstants.HEADER_MAPPING)
                .build();

        UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource");
    }

    @Test
    public void testValidTargetAddress() {
        UNDER_TEST.validate(connectionWithTarget("ditto/rabbit"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
        UNDER_TEST.validate(connectionWithTarget("ditto"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        UNDER_TEST.validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
        UNDER_TEST.validate(connectionWithTarget("ditto/{{thing:id}}/{{feature:id}}"), DittoHeaders.empty(),
                actorSystem, connectivityConfig);
        UNDER_TEST.validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
        UNDER_TEST.validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidHeaderMappingThrowsException() {
        final Map<String, String> mapping = new HashMap<>(TestConstants.HEADER_MAPPING.getMapping());
        mapping.put("thingId", "{{ thing:invalid }}");

        final Source source = newSourceBuilder()
                .headerMapping(ConnectivityModelFactory.newHeaderMapping(mapping))
                .build();

        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource"))
                .withCauseInstanceOf(UnresolvedPlaceholderException.class);
    }

    @Test
    public void testInvalidInputThrowsException() {
        final Source source = newSourceBuilder()
                .enforcement(ConnectivityModelFactory.newEnforcement("{{ thing:id }}", "{{ thing:namespace }}"))
                .build();

        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource"))
                .withCauseInstanceOf(UnresolvedPlaceholderException.class);
    }

    @Test
    public void testInvalidMatcherThrowsException() {
        final Source source = newSourceBuilder()
                .enforcement(ConnectivityModelFactory.newEnforcement(
                        "{{ header:device_id }}", "{{ header:ditto }}"))
                .build();

        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource"))
                .withCauseInstanceOf(UnresolvedPlaceholderException.class);
    }

    private static SourceBuilder newSourceBuilder() {
        return ConnectivityModelFactory.newSourceBuilder()
                .address("telemetry/device")
                .authorizationContext(
                        TestConstants.Authorization.AUTHORIZATION_CONTEXT);
    }

    private static Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.AMQP_091, ConnectivityStatus.OPEN, "amqp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(target)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .build();
    }

}
