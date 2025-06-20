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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigInvalidException;
import org.junit.jupiter.api.Test;

class MergeDynamicConfigSectionTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DynamicValidationConfig section = DynamicValidationConfig.of(scopeId, null, null);
        DittoHeaders headers = DittoHeaders.empty();
        MergeDynamicConfigSection command = MergeDynamicConfigSection.of(configId, scopeId, section, headers);
        assertEquals(configId, command.getEntityId());
        assertEquals(scopeId, command.getScopeId());
        assertEquals(section, command.getDynamicConfigSection());
        assertEquals(headers, command.getDittoHeaders());
    }

    @Test
    void testFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DittoHeaders headers = DittoHeaders.empty();
        JsonObject json = JsonFactory.newObjectBuilder()
                .set("configId", configId.toString())
                .set("scopeId", scopeId)
                .set("validationContext", JsonFactory.newObjectBuilder().build())
                .set("configOverrides", JsonFactory.newObjectBuilder().build())
                .build();
        MergeDynamicConfigSection parsed = MergeDynamicConfigSection.fromJson(json, headers);
        assertEquals(configId, parsed.getEntityId());
        assertEquals(scopeId, parsed.getScopeId());
    }


    @Test
    void testScopeIdMismatch() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        String scopeId = "scope1";
        DynamicValidationConfig section = DynamicValidationConfig.of("other-scope", null, null);
        DittoHeaders headers = DittoHeaders.empty();
        assertThrows(WotValidationConfigInvalidException.class, () -> MergeDynamicConfigSection.of(configId, scopeId, section, headers));
    }
} 