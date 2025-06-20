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

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;
import org.junit.jupiter.api.Test;

class ModifyWotValidationConfigTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfig config = WotValidationConfig.of(
                configId, true, false, null, null, Collections.emptyList(), WotValidationConfigRevision.of(1L), Instant.now(), Instant.now(), false, null
        );
        ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, config, headers);
        assertEquals(configId, command.getEntityId());
        assertEquals(headers, command.getDittoHeaders());
        assertEquals(config, command.getValidationConfig());
    }

    @Test
    void testFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        Instant now = Instant.now();
        WotValidationConfig config = WotValidationConfig.of(
                configId, true, false, null, null, Collections.emptyList(), WotValidationConfigRevision.of(1L), now, now, false, null
        );

        JsonObject json = JsonFactory.newObjectBuilder()
                .set(WotValidationConfigCommand.JsonFields.CONFIG_ID, configId.toString())
                .set(ModifyWotValidationConfig.JsonFields.VALIDATION_CONFIG, config.toJson())
                .build();
        ModifyWotValidationConfig parsed = ModifyWotValidationConfig.fromJson(json, headers);
        assertEquals(configId, parsed.getEntityId());
        assertEquals(config, parsed.getValidationConfig());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfig config = WotValidationConfig.of(
                configId, true, false, null, null, Collections.emptyList(), WotValidationConfigRevision.of(1L), Instant.now(), Instant.now(), false, null
        );
        ModifyWotValidationConfig c1 = ModifyWotValidationConfig.of(configId, config, headers);
        ModifyWotValidationConfig c2 = ModifyWotValidationConfig.of(configId, config, headers);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void testNullArguments() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfig config = WotValidationConfig.of(
                configId, true, false, null, null, Collections.emptyList(), WotValidationConfigRevision.of(1L), Instant.now(), Instant.now(), false, null
        );
        assertThrows(NullPointerException.class, () -> ModifyWotValidationConfig.of(null, config, headers));
        assertThrows(NullPointerException.class, () -> ModifyWotValidationConfig.of(configId, null, headers));
        assertThrows(NullPointerException.class, () -> ModifyWotValidationConfig.of(configId, config, null));
    }
} 