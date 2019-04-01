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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.junit.Test;

/**
 * Tests {@link HeaderTranslator}
 */
public final class HeaderTranslatorTest {

    @Test
    public void testCaseInsensitivity() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put("lower-case%header", "hello%world");
        externalHeaders.put("mIxEd-Case!HEadER", "heLLO!WORld");
        externalHeaders.put("UPPER-CASE@HEADER", "HELLO@WORLD");

        final Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("lower-case%header", "hello%world");
        expectedHeaders.put("mixed-case!header", "heLLO!WORld");
        expectedHeaders.put("upper-case@header", "HELLO@WORLD");

        assertThat(underTest.fromExternalHeaders(externalHeaders)).isEqualTo(expectedHeaders);
    }

    @Test
    public void testNullValues() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put("nullValueHeader", null);

        assertThat(underTest.fromExternalHeaders(externalHeaders)).isEmpty();
    }

}
