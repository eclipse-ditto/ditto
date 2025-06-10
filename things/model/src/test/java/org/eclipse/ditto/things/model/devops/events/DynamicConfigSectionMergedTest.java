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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class DynamicConfigSectionMergedTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        JsonPointer pointer = JsonPointer.of("/dynamicConfig/scope1");
        DynamicValidationConfig sectionValue = DynamicValidationConfig.of("scope1", null, null);
        long revision = 1L;
        Instant now = Instant.now();
        DittoHeaders headers = DittoHeaders.empty();
        DynamicConfigSectionMerged event = DynamicConfigSectionMerged.of(configId, pointer, sectionValue, revision, now, headers, null);
        assertEquals(configId, event.getEntityId());
        assertEquals(pointer, event.getResourcePath());
        assertEquals(sectionValue, event.getSectionValue());
        assertEquals(revision, event.getRevision());
        assertNotNull(event.getDittoHeaders());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        JsonPointer pointer = JsonPointer.of("/dynamicConfig/scope1");

        DynamicValidationConfig sectionValue = DynamicValidationConfig.of("scope1", null, null);
        long revision = 1L;
        Instant now = Instant.now();
        DittoHeaders headers = DittoHeaders.empty();
        DynamicConfigSectionMerged e1 = DynamicConfigSectionMerged.of(configId, pointer, sectionValue, revision, now, headers, null);
        DynamicConfigSectionMerged e2 = DynamicConfigSectionMerged.of(configId, pointer, sectionValue, revision, now, headers, null);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void testToJsonAndFromJson() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        JsonPointer pointer = JsonPointer.of("/dynamicConfig/scope1");
        DynamicValidationConfig sectionValue = DynamicValidationConfig.of("scope1", null, null);
        long revision = 1L;
        Instant now = Instant.now();
        DittoHeaders headers = DittoHeaders.empty();
        DynamicConfigSectionMerged event = DynamicConfigSectionMerged.of(configId, pointer, sectionValue, revision, now, headers, null);
        JsonObject json = event.toJson();
        DynamicConfigSectionMerged parsed = DynamicConfigSectionMerged.fromJson(json, headers);
        assertEquals(event, parsed);
    }

} 