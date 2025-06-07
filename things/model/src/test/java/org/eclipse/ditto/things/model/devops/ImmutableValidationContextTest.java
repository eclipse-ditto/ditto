/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.junit.jupiter.api.Test;

class ImmutableValidationContextTest {


    @Test
    void testEqualsAndHashCode() {
        ImmutableValidationContext context1 = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        ImmutableValidationContext context2 = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableValidationContext context = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        JsonObject json = context.toJson();
        ImmutableValidationContext parsed = ImmutableValidationContext.fromJson(json);
        assertEquals(context, parsed);
    }


    @Test
    void testWithNonEmptyPatterns() {
        // Example header pattern: a map with a header key and value
        Map<String, String> headerPattern = new HashMap<>();
        headerPattern.put("Authorization", "Bearer *");
        List<Map<String, String>> dittoHeadersPatterns = Collections.singletonList(headerPattern);

        // Example thing/feature patterns
        List<String> thingPatterns = Arrays.asList("thing:.*", "thing:special");
        List<String> featurePatterns = Arrays.asList("feature:.*", "feature:extra");

        ImmutableValidationContext context = ImmutableValidationContext.of(
            dittoHeadersPatterns, thingPatterns, featurePatterns
        );

        assertEquals(dittoHeadersPatterns, context.getDittoHeadersPatterns());
        assertEquals(thingPatterns, context.getThingDefinitionPatterns());
        assertEquals(featurePatterns, context.getFeatureDefinitionPatterns());
    }
} 