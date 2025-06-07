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

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.junit.jupiter.api.Test;

class RetrieveMergedWotValidationConfigResponseTest {
    @Test
    void testConstructionAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        Instant now = Instant.now();
        WotValidationConfig config = WotValidationConfig.of(
                configId,
                true,
                false,
                null,
                null,
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                now,
                now,
                false,
                null
        );
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveMergedWotValidationConfigResponse response = RetrieveMergedWotValidationConfigResponse.of(config, headers);
        assertThat(response.getConfig()).isEqualTo(config);
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        Instant now = Instant.now();
        WotValidationConfig config = WotValidationConfig.of(
                configId,
                true,
                false,
                null,
                null,
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                now,
                now,
                false,
                null
        );
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveMergedWotValidationConfigResponse r1 = RetrieveMergedWotValidationConfigResponse.of(config, headers);
        RetrieveMergedWotValidationConfigResponse r2 = RetrieveMergedWotValidationConfigResponse.of(config, headers);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void testSerializationDeserialization() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        Instant now = Instant.now();
        WotValidationConfig config = WotValidationConfig.of(
                configId,
                true,
                false,
                null,
                null,
                Collections.emptyList(),
                WotValidationConfigRevision.of(1L),
                now,
                now,
                false,
                null
        );
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveMergedWotValidationConfigResponse response = RetrieveMergedWotValidationConfigResponse.of(config, headers);
        JsonObject json = response.toJson();
        RetrieveMergedWotValidationConfigResponse fromJson = RetrieveMergedWotValidationConfigResponse.fromJson(json, headers);
        assertThat(fromJson).isEqualTo(response);
    }
} 