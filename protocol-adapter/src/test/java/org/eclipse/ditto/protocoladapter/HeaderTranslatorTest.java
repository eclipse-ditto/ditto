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

}
