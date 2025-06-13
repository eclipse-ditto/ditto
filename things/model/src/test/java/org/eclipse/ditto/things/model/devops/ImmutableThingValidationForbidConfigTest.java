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

class ImmutableThingValidationForbidConfigTest {

    @Test
    void testOfAndGetters() {
        ImmutableThingValidationForbidConfig config = ImmutableThingValidationForbidConfig.of(true, false, true, false);
        assertEquals(true, config.isThingDescriptionDeletion().orElse(null));
        assertEquals(false, config.isNonModeledAttributes().orElse(null));
        assertEquals(true, config.isNonModeledInboxMessages().orElse(null));
        assertEquals(false, config.isNonModeledOutboxMessages().orElse(null));
    }

    @Test
    void testEqualsAndHashCode() {
        ImmutableThingValidationForbidConfig config1 = ImmutableThingValidationForbidConfig.of(true, false, true, false);
        ImmutableThingValidationForbidConfig config2 = ImmutableThingValidationForbidConfig.of(true, false, true, false);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToJsonAndFromJsonRoundtrip() {
        ImmutableThingValidationForbidConfig config = ImmutableThingValidationForbidConfig.of(true, false, true, false);
        JsonObject json = config.toJson();
        ImmutableThingValidationForbidConfig parsed = ImmutableThingValidationForbidConfig.fromJson(json);
        assertEquals(config, parsed);
    }

    @Test
    void testNullAndEmptyHandling() {
        ImmutableThingValidationForbidConfig config = ImmutableThingValidationForbidConfig.of(null, null, null, null);
        assertFalse(config.isThingDescriptionDeletion().isPresent());
        assertFalse(config.isNonModeledAttributes().isPresent());
        assertFalse(config.isNonModeledInboxMessages().isPresent());
        assertFalse(config.isNonModeledOutboxMessages().isPresent());
    }
} 