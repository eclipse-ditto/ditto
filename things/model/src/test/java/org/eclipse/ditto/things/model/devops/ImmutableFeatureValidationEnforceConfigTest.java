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

class ImmutableFeatureValidationEnforceConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableFeatureValidationEnforceConfig config = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        assertEquals(true, config.isFeatureDescriptionModification().orElse(null));
        assertEquals(false, config.isPresenceOfModeledFeatures().orElse(null));
        assertEquals(true, config.isProperties().orElse(null));
        assertEquals(false, config.isDesiredProperties().orElse(null));
        assertEquals(true, config.isInboxMessagesInput().orElse(null));
        assertEquals(false, config.isInboxMessagesOutput().orElse(null));
        assertEquals(true, config.isOutboxMessages().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableFeatureValidationEnforceConfig config1 = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        ImmutableFeatureValidationEnforceConfig config2 = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableFeatureValidationEnforceConfig config = ImmutableFeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        JsonObject json = config.toJson();
        ImmutableFeatureValidationEnforceConfig parsed = ImmutableFeatureValidationEnforceConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableFeatureValidationEnforceConfig config = ImmutableFeatureValidationEnforceConfig.of(null, null, null, null, null, null, null);
        assertFalse(config.isFeatureDescriptionModification().isPresent());
        assertFalse(config.isPresenceOfModeledFeatures().isPresent());
        assertFalse(config.isProperties().isPresent());
        assertFalse(config.isDesiredProperties().isPresent());
        assertFalse(config.isInboxMessagesInput().isPresent());
        assertFalse(config.isInboxMessagesOutput().isPresent());
        assertFalse(config.isOutboxMessages().isPresent());
    }
} 