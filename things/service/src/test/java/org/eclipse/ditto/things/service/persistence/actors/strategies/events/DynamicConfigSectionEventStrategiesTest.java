/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.ConfigOverrides;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.ValidationContext;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionDeleted;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged;
import org.junit.Test;

/**
 * Tests that the dynamic config section event strategies advance the {@code modified} timestamp of the entity,
 * while leaving {@code created} untouched.
 */
public final class DynamicConfigSectionEventStrategiesTest {

    private static final WotValidationConfigId CONFIG_ID = WotValidationConfigId.GLOBAL;
    private static final String SCOPE_ID = "some-scope";
    private static final Instant CREATED = Instant.parse("2025-06-24T21:08:16.949342771Z");
    private static final Instant PREVIOUSLY_MODIFIED = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-08-28T10:00:00Z");

    private static DynamicValidationConfig section(final String scopeId) {
        return DynamicValidationConfig.of(scopeId,
                ValidationContext.of(List.of(), List.of("^https://example.org/.*$"), List.of()),
                ConfigOverrides.of(true, true, null, null));
    }

    private static WotValidationConfig entityWith(final DynamicValidationConfig... sections) {
        return WotValidationConfig.of(CONFIG_ID, true, false, null, null, List.of(sections),
                WotValidationConfigRevision.of(41L), CREATED, PREVIOUSLY_MODIFIED, false, null);
    }

    @Test
    public void mergedSectionAdvancesModifiedAndKeepsCreated() {
        final WotValidationConfig entity = entityWith();
        final DynamicConfigSectionMerged event = DynamicConfigSectionMerged.of(CONFIG_ID,
                JsonPointer.of("/dynamicConfig/" + SCOPE_ID), section(SCOPE_ID), 42L, EVENT_TIMESTAMP,
                DittoHeaders.empty(), null);

        final WotValidationConfig result = new DynamicConfigSectionMergedStrategy().handle(event, entity, 42L);

        assertThat(result).isNotNull();
        assertThat(result.getDynamicConfigs()).containsExactly(section(SCOPE_ID));
        assertThat(result.getRevision()).contains(WotValidationConfigRevision.of(42L));
        assertThat(result.getCreated()).contains(CREATED);
        assertThat(result.getModified()).contains(EVENT_TIMESTAMP);
    }

    @Test
    public void deletedSectionAdvancesModifiedAndKeepsCreated() {
        final WotValidationConfig entity = entityWith(section(SCOPE_ID));
        final DynamicConfigSectionDeleted event = DynamicConfigSectionDeleted.of(CONFIG_ID,
                JsonPointer.of("/dynamicConfig/" + SCOPE_ID), SCOPE_ID, 42L, EVENT_TIMESTAMP,
                DittoHeaders.empty(), null);

        final WotValidationConfig result = new DynamicConfigSectionDeletedStrategy().handle(event, entity, 42L);

        assertThat(result).isNotNull();
        assertThat(result.getDynamicConfigs()).isEmpty();
        assertThat(result.getRevision()).contains(WotValidationConfigRevision.of(42L));
        assertThat(result.getCreated()).contains(CREATED);
        assertThat(result.getModified()).contains(EVENT_TIMESTAMP);
    }

    @Test
    public void modifiedIsKeptWhenTheEventCarriesNoTimestamp() {
        final WotValidationConfig entity = entityWith();
        final DynamicConfigSectionMerged event = DynamicConfigSectionMerged.of(CONFIG_ID,
                JsonPointer.of("/dynamicConfig/" + SCOPE_ID), section(SCOPE_ID), 42L, null,
                DittoHeaders.empty(), null);

        final WotValidationConfig result = new DynamicConfigSectionMergedStrategy().handle(event, entity, 42L);

        assertThat(result).isNotNull();
        assertThat(result.getCreated()).contains(CREATED);
        assertThat(result.getModified()).contains(PREVIOUSLY_MODIFIED);
    }
}
