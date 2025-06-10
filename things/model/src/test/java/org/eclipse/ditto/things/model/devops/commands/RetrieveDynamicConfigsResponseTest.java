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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

class RetrieveDynamicConfigsResponseTest {
    @Test
    void testConstructionAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonArray dynamicConfigs = JsonFactory.newArrayBuilder().add(JsonFactory.newObjectBuilder().set("scopeId", "scope1").build()).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigsResponse response = RetrieveDynamicConfigsResponse.of(configId, dynamicConfigs, headers);
        assertThat(response.getConfigId().equals(configId)).isTrue();
        assertThat(response.getDynamicConfigs()).isEqualTo(dynamicConfigs);
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonArray dynamicConfigs = JsonFactory.newArrayBuilder().add(JsonFactory.newObjectBuilder().set("scopeId", "scope1").build()).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigsResponse r1 = RetrieveDynamicConfigsResponse.of(configId, dynamicConfigs, headers);
        RetrieveDynamicConfigsResponse r2 = RetrieveDynamicConfigsResponse.of(configId, dynamicConfigs, headers);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void testSerializationDeserialization() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        JsonArray dynamicConfigs = JsonFactory.newArrayBuilder().add(JsonFactory.newObjectBuilder().set("scopeId", "scope1").build()).build();
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveDynamicConfigsResponse response = RetrieveDynamicConfigsResponse.of(configId, dynamicConfigs, headers);
        JsonObject json = response.toJson();
        RetrieveDynamicConfigsResponse fromJson = RetrieveDynamicConfigsResponse.fromJson(json, headers);
        assertThat(fromJson).isEqualTo(response);
    }
} 