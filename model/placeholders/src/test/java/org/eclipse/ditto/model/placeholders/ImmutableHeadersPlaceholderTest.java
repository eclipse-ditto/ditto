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
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

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

    static {
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
        assertThat(UNDER_TEST.apply(HEADERS, "device_id")).contains(DEVICE_ID);
    }

    @Test
    public void testUnresolvableHeaderReturnsEmpty() {
        assertThat(UNDER_TEST.apply(HEADERS, "thing_id")).isEmpty();
    }
}
