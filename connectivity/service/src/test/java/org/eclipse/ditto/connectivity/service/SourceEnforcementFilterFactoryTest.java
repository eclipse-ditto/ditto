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

import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.junit.Assert.assertNotNull;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class SourceEnforcementFilterFactoryTest {

    @Test
    public void testSourceAddressEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ source:address }}",
                "{{ thing:namespace }}.{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter =
                SourceEnforcementFilterFactory.of(source).getFilter(Map.of(), "org.eclipse.ditto.sensor");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=org.eclipse.ditto.sensor]");
    }

    @Test
    public void testHeaderDeviceIdEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                "{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter =
                SourceEnforcementFilterFactory.of(source).getFilter(Map.of("device_id", "sensor"),
                        "org.eclipse.ditto.sensor");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=sensor]");
    }

    @Test
    public void testNoEnforcementCreatesNoFilter() {
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .build();

        final EnforcementFilter<Signal<?>> filter =
                SourceEnforcementFilterFactory.of(source).getFilter(Map.of(), "org.eclipse.ditto.sensor");

        Assert.assertNull(filter);
    }

    @Test
    public void testNoSpacesSourceEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(
                "{{source:address}}", "{{ thing:namespace }}.{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter =
                SourceEnforcementFilterFactory.of(source)
                        .getFilter(Map.of(), "org.eclipse.ditto.sensor");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=org.eclipse.ditto.sensor]");
    }

    @Test
    public void testSourceAndHeaderEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(
                "{{source:address}}@{{ header:device_id }}",
                "{{ thing:namespace }}.{{ thing:name }}@{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter = SourceEnforcementFilterFactory.of(source)
                .getFilter(Map.of("device_id", "sensor"), "org.eclipse.ditto.sensor");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=org.eclipse.ditto.sensor@sensor]");
    }

    @Test
    public void testHeaderDefaultFunctionEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(
                "{{ header:unknown | fn:default('fallback') }}",
                "fallback");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter = SourceEnforcementFilterFactory.of(source)
                .getFilter(Map.of(), "org.eclipse.ditto.sensor");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=fallback]");
    }

    @Test
    public void testHeaderSubstringFunctionEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement(
                "{{ source:address | fn:substring-after('/') }}",
                "fallback");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor/555")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final EnforcementFilter<Signal<?>> filter = SourceEnforcementFilterFactory.of(source)
                .getFilter(Map.of(), "org.eclipse.ditto.sensor/555");

        assertNotNull(filter);
        assertThat(filter.toString()).endsWith("inputValue=555]");
    }

    @Test
    public void testHeaderInvalidEnforcementThrows() {
    final Enforcement enforcement =
        ConnectivityModelFactory.newEnforcement("{{ header:foo }}", "{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("org.eclipse.ditto.sensor")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        Assertions.assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> SourceEnforcementFilterFactory.of(source)
                .getFilter(Map.of(), "org.eclipse.ditto.sensor"));
    }

}
