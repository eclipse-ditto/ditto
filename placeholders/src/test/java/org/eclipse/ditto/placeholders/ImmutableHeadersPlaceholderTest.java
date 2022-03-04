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
package org.eclipse.ditto.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableHeadersPlaceholder}.
 */
public class ImmutableHeadersPlaceholderTest {

    private static final ImmutableHeadersPlaceholder UNDER_TEST = ImmutableHeadersPlaceholder.INSTANCE;
    private static final Map<String, String> HEADERS = new HashMap<>();

    private static final String DEVICE_ID = "eclipse:ditto:device1234";

    @BeforeClass
    public static void setUp() {
        HEADERS.put("device_id", DEVICE_ID);
        HEADERS.put("correlation_id", "4205833931151659498");
    }

    /**
     * Assert immutability.
     */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableHeadersPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    /**
     * Test hash code and equals.
     */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableHeadersPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceDeviceIdHeader() {
        assertThat(UNDER_TEST.resolveValues(HEADERS, "device_id")).contains(DEVICE_ID);
    }

    @Test
    public void testUnresolvableHeaderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(HEADERS, "thing_id")).isEmpty();
    }

    @Test
    public void testResolvingWithNull() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> UNDER_TEST.resolveValues(HEADERS, null));
    }

    @Test
    public void testResolvingWithEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> UNDER_TEST.resolveValues(HEADERS, ""));
    }

}
