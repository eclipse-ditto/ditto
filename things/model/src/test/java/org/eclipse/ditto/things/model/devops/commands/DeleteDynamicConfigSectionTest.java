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
package org.eclipse.ditto.things.model.devops.commands;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

class DeleteDynamicConfigSectionTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DittoHeaders headers = DittoHeaders.empty();
        DeleteDynamicConfigSection command = DeleteDynamicConfigSection.of(configId, scopeId, headers);
        assertEquals(configId, command.getEntityId());
        assertEquals(scopeId, command.getScopeId());
        assertEquals(headers, command.getDittoHeaders());
    }

    @Test
    void testFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DittoHeaders headers = DittoHeaders.empty();
        JsonObject json = JsonFactory.newObjectBuilder()
                .set(WotValidationConfigCommand.JsonFields.CONFIG_ID, configId.toString())
                .set("scope-id", scopeId)
                .build();
        DeleteDynamicConfigSection parsed = DeleteDynamicConfigSection.fromJson(json, headers);
        assertEquals(configId, parsed.getEntityId());
        assertEquals(scopeId, parsed.getScopeId());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DittoHeaders headers = DittoHeaders.empty();
        DeleteDynamicConfigSection c1 = DeleteDynamicConfigSection.of(configId, scopeId, headers);
        DeleteDynamicConfigSection c2 = DeleteDynamicConfigSection.of(configId, scopeId, headers);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void testNullArguments() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DittoHeaders headers = DittoHeaders.empty();
        assertThrows(NullPointerException.class, () -> DeleteDynamicConfigSection.of(null, scopeId, headers));
        assertThrows(NullPointerException.class, () -> DeleteDynamicConfigSection.of(configId, null, headers));
        assertThrows(NullPointerException.class, () -> DeleteDynamicConfigSection.of(configId, scopeId, null));
    }
} 