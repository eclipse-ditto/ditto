/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.gateway.endpoints.utils.TraceUriGenerator.FALLBACK_PATH;
import static org.eclipse.ditto.services.gateway.endpoints.utils.TraceUriGenerator.MESSAGES_PATH_SUFFIX;
import static org.eclipse.ditto.services.gateway.endpoints.utils.TraceUriGenerator.SHORTENED_PATH_SUFFIX;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TraceUriGenerator}.
 */
public final class TraceUriGeneratorTest {

    private static final TraceUriGenerator UNDER_TEST = TraceUriGenerator.getInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TraceUriGenerator.class,
                areImmutable(),
                // according to JavaDoc, Pattern is immutable!
                provided(Pattern.class).isAlsoImmutable());
    }

    @Ignore("TraceUriGenerator is a Singleton and thus always equal to itself")
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TraceUriGenerator.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void api1ThingsMessagesUriReturnsCorrectSuffix() {
        assertTraceUri("/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/inbox/messages/randomMessageSubject",
                "/api/1/things" + MESSAGES_PATH_SUFFIX);
    }

    @Test
    public void api1ThingsClaimMessagesUriReturnsCorrectSuffix() {
        assertTraceUri("api/1/things/abcdefgh:fancy-car/inbox/claim",
                "/api/1/things" + MESSAGES_PATH_SUFFIX);
    }

    @Test
    public void api2ThingsUriIsShortened() {
        assertTraceUri("/api/2/things/ns:1", "/api/2/things" + SHORTENED_PATH_SUFFIX);
    }

    @Test
    public void api2ThingsSearchUriIsShortened() {
        assertTraceUri("/api/2/search/things", "/api/2/search/things" + SHORTENED_PATH_SUFFIX);
    }

    @Test
    public void api2ThingsSearchCountUriIsShortened() {
        assertTraceUri("/api/2/search/things/count", "/api/2/search/things" + SHORTENED_PATH_SUFFIX);
    }

    @Test
    public void statusUriRemainsTheSame() {
        assertTraceUri("/status", "/status");
    }

    @Test
    public void statusHealthUriRemainsTheSame() {
        assertTraceUri("/status/health", "/status/health");
    }

    @Test
    public void nonExistingStatusSubUriReturnsFallback() {
        assertTraceUri("/status/bumlux", FALLBACK_PATH);
    }

    @Test
    public void nonExistingUriStartingWithStatusReturnsFallback() {
        assertTraceUri("/status1", FALLBACK_PATH);
    }

    @Test
    public void onExistingUriStartingWithStatusHealthReturnsFallback() {
        assertTraceUri("/status/healthTest", FALLBACK_PATH);
    }

    @Test
    public void nonExistingApiSubUriReturnsFallback() {
        assertTraceUri("/api/9/search/things", FALLBACK_PATH);
    }

    @Test
    public void nonExistingApiSubUriStartingWithThingsReturnsFallback() {
        assertTraceUri("/api/9/search/thingsX", FALLBACK_PATH);
    }

    @Test
    public void nonExistingApi2SubUriReturnsFallback() {
        assertTraceUri("/api/2/bumlux", FALLBACK_PATH);
    }

    @Test
    public void nonExistingSearchSubUriReturnsFallback() {
        assertTraceUri("/api/2/search/bumlux", FALLBACK_PATH);
    }

    @Test
    public void nonExistingRootSubUriReturnsFallback() {
        assertTraceUri("/bumlux", FALLBACK_PATH);
    }

    private static void assertTraceUri(final String requestUri, final String expectedTraceUri) {
        assertThat(UNDER_TEST.apply(requestUri)).isEqualTo(expectedTraceUri);
    }

}
