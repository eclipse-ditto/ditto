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

class ImmutableThingValidationEnforceConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableThingValidationEnforceConfig config = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        assertEquals(true, config.isThingDescriptionModification().orElse(null));
        assertEquals(false, config.isAttributes().orElse(null));
        assertEquals(true, config.isInboxMessagesInput().orElse(null));
        assertEquals(false, config.isInboxMessagesOutput().orElse(null));
        assertEquals(true, config.isOutboxMessages().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableThingValidationEnforceConfig config1 = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        ImmutableThingValidationEnforceConfig config2 = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableThingValidationEnforceConfig config = ImmutableThingValidationEnforceConfig.of(true, false, true, false, true);
        JsonObject json = config.toJson();
        ImmutableThingValidationEnforceConfig parsed = ImmutableThingValidationEnforceConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableThingValidationEnforceConfig config = ImmutableThingValidationEnforceConfig.of(null, null, null, null, null);
        assertFalse(config.isThingDescriptionModification().isPresent());
        assertFalse(config.isAttributes().isPresent());
        assertFalse(config.isInboxMessagesInput().isPresent());
        assertFalse(config.isInboxMessagesOutput().isPresent());
        assertFalse(config.isOutboxMessages().isPresent());
    }
} 