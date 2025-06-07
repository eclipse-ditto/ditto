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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.junit.jupiter.api.Test;

class ImmutableDynamicValidationConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(null, null, null, null);
        ImmutableDynamicValidationConfig
                config = ImmutableDynamicValidationConfig.of("scope1", validationContext, configOverrides);
        assertEquals("scope1", config.getScopeId());
        assertEquals(validationContext, config.getValidationContext().orElse(null));
        assertEquals(configOverrides, config.getConfigOverrides().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(null, null, null, null);
        ImmutableDynamicValidationConfig
                config1 = ImmutableDynamicValidationConfig.of("scope1", validationContext, configOverrides);
        ImmutableDynamicValidationConfig
                config2 = ImmutableDynamicValidationConfig.of("scope1", validationContext, configOverrides);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(null, null, null, null);
        ImmutableDynamicValidationConfig
                config = ImmutableDynamicValidationConfig.of("scope1", validationContext, configOverrides);
        JsonObject json = config.toJson();
        ImmutableDynamicValidationConfig parsed = ImmutableDynamicValidationConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableDynamicValidationConfig config = ImmutableDynamicValidationConfig.of("scope2", null, null);
        assertEquals("scope2", config.getScopeId());
        assertFalse(config.getValidationContext().isPresent());
        assertFalse(config.getConfigOverrides().isPresent());
    }

    @Test
    void testWithNonEmptyData() {
        // Non-empty validation context
        Map<String, String> headerPattern = new HashMap<>();
        headerPattern.put("Authorization", "Bearer *");
        List<Map<String, String>> dittoHeadersPatterns = Collections.singletonList(headerPattern);
        List<String> thingPatterns = Arrays.asList("thing:.*", "thing:special");
        List<String> featurePatterns = Arrays.asList("feature:.*", "feature:extra");
        ImmutableValidationContext validationContext = ImmutableValidationContext.of(
            dittoHeadersPatterns, thingPatterns, featurePatterns
        );

        // Non-empty config overrides
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(null, null);
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(null, null);
        ImmutableConfigOverrides configOverrides = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);

        ImmutableDynamicValidationConfig
                config = ImmutableDynamicValidationConfig.of("scope1", validationContext, configOverrides);
        assertEquals("scope1", config.getScopeId());
        assertEquals(validationContext, config.getValidationContext().orElse(null));
        assertEquals(configOverrides, config.getConfigOverrides().orElse(null));
    }
} 