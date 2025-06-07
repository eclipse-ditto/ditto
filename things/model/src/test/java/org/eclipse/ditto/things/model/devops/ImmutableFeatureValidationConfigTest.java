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

class ImmutableFeatureValidationConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableFeatureValidationEnforceConfig enforce = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        ImmutableFeatureValidationForbidConfig forbid = ImmutableFeatureValidationForbidConfig.of(false, true, false, true, false, true);
        ImmutableFeatureValidationConfig config = ImmutableFeatureValidationConfig.of(enforce, forbid);
        assertEquals(enforce, config.getEnforce().orElse(null));
        assertEquals(forbid, config.getForbid().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableFeatureValidationEnforceConfig enforce = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        ImmutableFeatureValidationForbidConfig forbid = ImmutableFeatureValidationForbidConfig.of(false, true, false, true, false, true);
        ImmutableFeatureValidationConfig config1 = ImmutableFeatureValidationConfig.of(enforce, forbid);
        ImmutableFeatureValidationConfig config2 = ImmutableFeatureValidationConfig.of(enforce, forbid);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableFeatureValidationEnforceConfig enforce = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        ImmutableFeatureValidationForbidConfig forbid = ImmutableFeatureValidationForbidConfig.of(false, true, false, true, false, true);
        ImmutableFeatureValidationConfig config = ImmutableFeatureValidationConfig.of(enforce, forbid);
        JsonObject json = config.toJson();
        ImmutableFeatureValidationConfig parsed = ImmutableFeatureValidationConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableFeatureValidationConfig config = ImmutableFeatureValidationConfig.of(null, null);
        assertFalse(config.getEnforce().isPresent());
        assertFalse(config.getForbid().isPresent());
    }
} 