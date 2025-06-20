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
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.junit.jupiter.api.Test;

class RetrieveAllDynamicConfigSectionsTest {

    @Test
    void testOfAndGetters() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveAllDynamicConfigSections command = RetrieveAllDynamicConfigSections.of(configId, headers);
        assertEquals(configId, command.getEntityId());
        assertEquals(headers, command.getDittoHeaders());
    }

    @Test
    void testEqualsAndHashCode() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        RetrieveAllDynamicConfigSections c1 = RetrieveAllDynamicConfigSections.of(configId, headers);
        RetrieveAllDynamicConfigSections c2 = RetrieveAllDynamicConfigSections.of(configId, headers);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void testNullArguments() {
        WotValidationConfigId configId = WotValidationConfigId.of("ns:test-id");
        DittoHeaders headers = DittoHeaders.empty();
        assertThrows(NullPointerException.class, () -> RetrieveAllDynamicConfigSections.of(null, headers));
        assertThrows(NullPointerException.class, () -> RetrieveAllDynamicConfigSections.of(configId, null));
    }
} 