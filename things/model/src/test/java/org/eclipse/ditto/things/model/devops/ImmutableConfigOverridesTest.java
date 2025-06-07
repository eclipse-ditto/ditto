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

import org.eclipse.ditto.json.JsonObject;
import org.junit.jupiter.api.Test;

class ImmutableConfigOverridesTest {

    @Test
    void testOfAndGetters() {
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(null, null);
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(null, null);
        ImmutableConfigOverrides config = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);
        assertEquals(true, config.isEnabled().orElse(null));
        assertEquals(false, config.isLogWarningInsteadOfFailingApiCalls().orElse(null));
        assertEquals(thingConfig, config.getThingConfig().orElse(null));
        assertEquals(featureConfig, config.getFeatureConfig().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(null, null);
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(null, null);
        ImmutableConfigOverrides config1 = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);
        ImmutableConfigOverrides config2 = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(null, null);
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(null, null);
        ImmutableConfigOverrides config = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);
        JsonObject json = config.toJson();
        ImmutableConfigOverrides parsed = ImmutableConfigOverrides.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableConfigOverrides config = ImmutableConfigOverrides.of(null, null, null, null);
        assertFalse(config.isEnabled().isPresent());
        assertFalse(config.isLogWarningInsteadOfFailingApiCalls().isPresent());
        assertFalse(config.getThingConfig().isPresent());
        assertFalse(config.getFeatureConfig().isPresent());
    }

    @Test
    void testWithNonEmptyConfigs() {
        // Non-empty thing and feature configs
        ImmutableThingValidationEnforceConfig thingEnforce = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        ImmutableThingValidationForbidConfig thingForbid = ImmutableThingValidationForbidConfig.of(false, true, false, true);
        ImmutableThingValidationConfig thingConfig = ImmutableThingValidationConfig.of(thingEnforce, thingForbid);

        ImmutableFeatureValidationEnforceConfig featureEnforce = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        ImmutableFeatureValidationForbidConfig featureForbid = ImmutableFeatureValidationForbidConfig.of(false, true, false, true, false, true);
        ImmutableFeatureValidationConfig featureConfig = ImmutableFeatureValidationConfig.of(featureEnforce, featureForbid);

        ImmutableConfigOverrides config = ImmutableConfigOverrides.of(true, false, thingConfig, featureConfig);
        assertEquals(true, config.isEnabled().orElse(null));
        assertEquals(false, config.isLogWarningInsteadOfFailingApiCalls().orElse(null));
        assertEquals(thingConfig, config.getThingConfig().orElse(null));
        assertEquals(featureConfig, config.getFeatureConfig().orElse(null));
    }
} 