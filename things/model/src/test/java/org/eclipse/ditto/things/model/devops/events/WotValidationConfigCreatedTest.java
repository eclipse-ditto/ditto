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
package org.eclipse.ditto.things.model.devops.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;


class WotValidationConfigCreatedTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        WotValidationConfig config = WotValidationConfig.of(configId, true, false, null, null, null, null, Instant.now(), Instant.now(), false, null);
        long revision = 1L;
        Instant now = Instant.now();
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfigCreated event = WotValidationConfigCreated.of(configId, config, revision, now, headers, null);
        assertEquals(configId, event.getEntityId());
        assertEquals(config, event.getConfig());
        assertEquals(revision, event.getRevision());
        assertNotNull(event.getDittoHeaders());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        WotValidationConfig config = WotValidationConfig.of(configId, true, false, null, null, null, null, Instant.now(), Instant.now(), false, null);
        long revision = 1L;
        Instant now = Instant.now();
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfigCreated e1 = WotValidationConfigCreated.of(configId, config, revision, now, headers, null);
        WotValidationConfigCreated e2 = WotValidationConfigCreated.of(configId, config, revision, now, headers, null);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void testToJsonAndFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        Instant now = Instant.now();
        WotValidationConfig config = WotValidationConfig.of(configId, true, false, null, null, null, null, now, now, false, null);
        long revision = 1L;
        DittoHeaders headers = DittoHeaders.empty();
        WotValidationConfigCreated event = WotValidationConfigCreated.of(configId, config, revision, now, headers, null);
        org.eclipse.ditto.json.JsonObject json = event.toJson();
        WotValidationConfigCreated parsed = WotValidationConfigCreated.fromJson(json, headers);
        assertEquals(event.getEntityId(), parsed.getEntityId());
        assertEquals(event.getConfig(), parsed.getConfig());
        assertEquals(event.getRevision(), parsed.getRevision());
    }
} 