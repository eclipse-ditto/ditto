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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceBuilder;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
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
}
