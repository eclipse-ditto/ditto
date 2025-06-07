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

class ImmutableThingValidationConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableThingValidationEnforceConfig enforce = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        ImmutableThingValidationForbidConfig forbid = ImmutableThingValidationForbidConfig.of(false, true, false, true);
        ImmutableThingValidationConfig config = ImmutableThingValidationConfig.of(enforce, forbid);
        assertEquals(enforce, config.getEnforce().orElse(null));
        assertEquals(forbid, config.getForbid().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableThingValidationEnforceConfig enforce = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        ImmutableThingValidationForbidConfig forbid = ImmutableThingValidationForbidConfig.of(false, true, false, true);
        ImmutableThingValidationConfig config1 = ImmutableThingValidationConfig.of(enforce, forbid);
        ImmutableThingValidationConfig config2 = ImmutableThingValidationConfig.of(enforce, forbid);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableThingValidationEnforceConfig enforce = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        ImmutableThingValidationForbidConfig forbid = ImmutableThingValidationForbidConfig.of(false, true, false, true);
        ImmutableThingValidationConfig config = ImmutableThingValidationConfig.of(enforce, forbid);
        JsonObject json = config.toJson();
        ImmutableThingValidationConfig parsed = ImmutableThingValidationConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableThingValidationConfig config = ImmutableThingValidationConfig.of(null, null);
        assertFalse(config.getEnforce().isPresent());
        assertFalse(config.getForbid().isPresent());
    }
} 