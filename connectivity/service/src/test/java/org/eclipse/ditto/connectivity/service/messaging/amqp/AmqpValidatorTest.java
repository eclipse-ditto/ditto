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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newTargetBuilder;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Test;

/**
 * Tests {@link AmqpValidator}.
 */
public final class AmqpValidatorTest {

    private static final AmqpValidator UNDER_TEST = AmqpValidator.newInstance();

    @Test
    public void testImmutability() {
        assertInstancesOf(AmqpValidator.class, areImmutable());
    }

    @Test
    public void testValidationOfEnforcement() {
        final Source source = newSourceBuilder()
                .enforcement(ConnectivityModelFactory.newEnforcement(
                        "{{ header:device_id }}", "{{ thing:id }}", "{{ thing:name }}", "{{ thing:namespace }}"))
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

    @Test
    public void testValidMatchers() {
        final Source source = newSourceBuilder()
                .enforcement(ConnectivityModelFactory.newEnforcement(
                        "{{ header:device_id }}", "{{ policy:id }}",
                        "{{ thing:id }}", "{{ entity:id }}"))
                .build();

        UNDER_TEST.validateSource(source, DittoHeaders.empty(), () -> "testSource");
    }

    @Test
    public void testValidPlaceholdersInTargetAddress() {
        final Target target = newTargetBuilder()
                .address("some.address.{{ topic:action-subject }}.{{ thing:id }}.{{ feature:id }}.{{ header:correlation-id }}")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .topics(Topic.LIVE_COMMANDS)
                .build();

        UNDER_TEST.validateTarget(target, DittoHeaders.empty(), () -> "testTarget");
    }

    private static SourceBuilder newSourceBuilder() {
        return ConnectivityModelFactory.newSourceBuilder()
                .address("telemetry/device")
                .authorizationContext(
                        AUTHORIZATION_CONTEXT);
    }
}
