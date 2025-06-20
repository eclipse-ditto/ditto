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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteWotValidationConfigResponseTest {
    @Test
    void testConstructionAndEquals() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        DittoHeaders headers = DittoHeaders.empty();
        DeleteWotValidationConfigResponse r1 = DeleteWotValidationConfigResponse.of(configId, headers);
        DeleteWotValidationConfigResponse r2 = DeleteWotValidationConfigResponse.of(configId, headers);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void testGetEntityIsEmpty() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test");
        DittoHeaders headers = DittoHeaders.empty();
        DeleteWotValidationConfigResponse response = DeleteWotValidationConfigResponse.of(configId, headers);
        assertThat(response.getEntity(null)).isEmpty();
    }
} 