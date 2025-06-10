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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged;

/**
 * Event strategy for handling {@link org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged} events.
 * <p>
 * This strategy applies a dynamic config section merge event to a WoT validation config entity, replacing any existing
 * section with the same scope ID and adding the new/updated section.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class DynamicConfigSectionMergedStrategy implements EventStrategy<DynamicConfigSectionMerged, WotValidationConfig> {

    DynamicConfigSectionMergedStrategy() {
        super();
    }

    @Nullable
    @Override
    public WotValidationConfig handle(final DynamicConfigSectionMerged event,
                                      @Nullable final WotValidationConfig entity,
                                      final long revision) {
        if (entity == null) {
            return null;
        }

        final String scopeId = event.getResourcePath().getLeaf().map(Object::toString).orElse("");
        if (scopeId.isEmpty()) {
            return entity;
        }

        final DynamicValidationConfig newSection = event.getSectionValue();

        final List<DynamicValidationConfig> updatedDynamicConfig = entity.getDynamicConfigs().stream()
                .filter(section -> !section.getScopeId().equals(scopeId))
                .collect(java.util.stream.Collectors.toList());
        updatedDynamicConfig.add(newSection);

        final WotValidationConfigId configId = event.getEntityId();

        return WotValidationConfig.of(
                configId,
                entity.isEnabled().orElse(null),
                entity.logWarningInsteadOfFailingApiCalls().orElse(null),
                entity.getThingConfig().orElse(null),
                entity.getFeatureConfig().orElse(null),
                updatedDynamicConfig,
                WotValidationConfigRevision.of(revision),
                entity.getCreated().orElse(null),
                entity.getModified().orElse(null),
                entity.isDeleted(),
                entity.getMetadata().orElse(null)
        );
    }
} 