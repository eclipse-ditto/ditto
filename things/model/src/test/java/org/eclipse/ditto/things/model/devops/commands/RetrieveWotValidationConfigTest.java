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
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;
import org.junit.jupiter.api.Test;

class RetrieveWotValidationConfigTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveWotValidationConfig command = RetrieveWotValidationConfig.of(configId, headers);
        assertEquals(configId, command.getEntityId());
        assertEquals(headers, command.getDittoHeaders());
    }

    @Test
    void testFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        JsonObject json = JsonFactory.newObjectBuilder()
                .set(WotValidationConfigCommand.JsonFields.CONFIG_ID, configId.toString())
                .build();
        RetrieveWotValidationConfig parsed = RetrieveWotValidationConfig.fromJson(json, headers);
        assertEquals(configId, parsed.getEntityId());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveWotValidationConfig c1 = RetrieveWotValidationConfig.of(configId, headers);
        RetrieveWotValidationConfig c2 = RetrieveWotValidationConfig.of(configId, headers);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void testNullArguments() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        assertThrows(NullPointerException.class, () -> RetrieveWotValidationConfig.of(null, headers));
        assertThrows(NullPointerException.class, () -> RetrieveWotValidationConfig.of(configId, null));
    }
} 