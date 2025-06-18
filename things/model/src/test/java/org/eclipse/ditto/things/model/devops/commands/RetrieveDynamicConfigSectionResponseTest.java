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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

class RetrieveDynamicConfigSectionResponseTest {

    private static final String KNOWN_SCOPE_ID = "knownScope";

    @Test
    void testConstructionAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonValue config = JsonFactory.newObjectBuilder().set("enabled", true).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigSectionResponse response =
                RetrieveDynamicConfigSectionResponse.of(configId, KNOWN_SCOPE_ID, config, headers);
        assertThat(response.getConfigId().equals(configId)).isTrue();
        assertThat(response.getScopeId()).isEqualTo(KNOWN_SCOPE_ID);
        assertThat(response.getValidationConfig()).isEqualTo(config);
        assertThat(response.getDittoHeaders().get("response-required")).isEqualTo("false");
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonValue config = JsonFactory.newObjectBuilder().set("enabled", true).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigSectionResponse r1 = RetrieveDynamicConfigSectionResponse.of(configId, KNOWN_SCOPE_ID, config, headers);
        RetrieveDynamicConfigSectionResponse r2 = RetrieveDynamicConfigSectionResponse.of(configId, KNOWN_SCOPE_ID, config, headers);
        assertThat(r1)
                .isEqualTo(r2)
                .hasSameHashCodeAs(r2);
    }

    @Test
    void testSerializationDeserialization() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonValue config = JsonFactory.newObjectBuilder().set("enabled", true).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigSectionResponse response =
                RetrieveDynamicConfigSectionResponse.of(configId, KNOWN_SCOPE_ID, config, headers);
        JsonObject json = response.toJson();
        RetrieveDynamicConfigSectionResponse fromJson = RetrieveDynamicConfigSectionResponse.fromJson(json, headers);
        assertThat(fromJson).isEqualTo(response);
    }
} 