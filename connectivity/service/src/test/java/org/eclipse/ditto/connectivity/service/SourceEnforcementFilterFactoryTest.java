/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Test;

/**
 * Tests {@link SourceEnforcementFilterFactory}.
 */
public final class SourceEnforcementFilterFactoryTest {

    private static final String SOURCE_ADDRESS = "org.eclipse.ditto.sensor";
    private static final Signal<?> SIGNAL_FOR_SENSOR = signalFor("org.eclipse.ditto:sensor");
    private static final Signal<?> SIGNAL_FOR_OTHER = signalFor("org.eclipse.ditto:other");

    @Test
    public void testSourceAddressEnforcementCreatesFilter() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ source:address }}",
                        "{{ thing:namespace }}.{{ thing:name }}"),
                SOURCE_ADDRESS).getFilter(Map.of());

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    @Test
    public void testHeaderDeviceIdEnforcementCreatesFilter() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ header:device_id }}", "{{ thing:name }}"),
                SOURCE_ADDRESS).getFilter(Map.of("device_id", "sensor"));

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    @Test
    public void testNoEnforcementCreatesNoFilter() {
        assertThat(factoryFor(null, SOURCE_ADDRESS).getFilter(Map.of())).isNull();
    }

    @Test
    public void testNoSpacesSourceEnforcementCreatesFilter() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{source:address}}",
                        "{{ thing:namespace }}.{{ thing:name }}"),
                SOURCE_ADDRESS).getFilter(Map.of());

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    @Test
    public void testSourceAndHeaderEnforcementCreatesFilter() {
        final SourceEnforcementFilterFactory underTest = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ source:address }}@{{ header:device_id }}",
                        "{{ thing:namespace }}.{{ thing:name }}@{{ thing:name }}"),
                SOURCE_ADDRESS);

        assertAccepts(underTest.getFilter(Map.of("device_id", "sensor")), SIGNAL_FOR_SENSOR);
        assertRejects(underTest.getFilter(Map.of("device_id", "other")), SIGNAL_FOR_SENSOR);
    }

    @Test
    public void testHeaderDefaultFunctionEnforcementCreatesFilter() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ header:device_id | fn:default('sensor') }}",
                        "{{ thing:name }}"),
                SOURCE_ADDRESS).getFilter(Map.of());

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    @Test
    public void testSourceSubstringFunctionEnforcementCreatesFilter() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ source:address | fn:substring-after('/') }}",
                        "{{ thing:name }}"),
                "devices/sensor").getFilter(Map.of());

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    @Test
    public void testHeaderInvalidEnforcementThrows() {
        final SourceEnforcementFilterFactory underTest = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ header:nope }}", "{{ thing:name }}"),
                SOURCE_ADDRESS);

        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.getFilter(Map.of()));
    }

    @Test
    public void explicitSourceAddressOverridesConsumerAddress() {
        final EnforcementFilter<Signal<?>> filter = factoryFor(
                ConnectivityModelFactory.newEnforcement("{{ source:address }}",
                        "{{ thing:namespace }}.{{ thing:name }}"),
                "org.eclipse.ditto.+").getFilter(Map.of(), SOURCE_ADDRESS);

        assertAccepts(filter, SIGNAL_FOR_SENSOR);
        assertRejects(filter, SIGNAL_FOR_OTHER);
    }

    private static SourceEnforcementFilterFactory factoryFor(@Nullable final Enforcement enforcement,
            final String sourceAddress) {
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address(sourceAddress)
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .build();
        return SourceEnforcementFilterFactory.of(source, sourceAddress);
    }

    private static Signal<?> signalFor(final String thingId) {
        return RetrieveThing.of(ThingId.of(thingId), DittoHeaders.empty());
    }

    private static void assertAccepts(final EnforcementFilter<Signal<?>> filter, final Signal<?> signal) {
        assertThatCode(() -> filter.match(signal)).doesNotThrowAnyException();
    }

    private static void assertRejects(final EnforcementFilter<Signal<?>> filter, final Signal<?> signal) {
        assertThatExceptionOfType(ConnectionSignalIdEnforcementFailedException.class)
                .isThrownBy(() -> filter.match(signal));
    }

}
