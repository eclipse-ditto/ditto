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

import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionDeleted;

/**
 * Event strategy for handling {@link DynamicConfigSectionDeleted} events.
 * <p>
 * This strategy applies a dynamic config section deletion event to a WoT validation config entity, removing the section
 * with the specified scope ID from the list of dynamic configs.
 * </p>
 *
 * @see org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionDeleted
 * @see org.eclipse.ditto.things.model.devops.commands.DeleteDynamicConfigSection
 * @since 3.8.0
 */
final class DynamicConfigSectionDeletedStrategy
        implements EventStrategy<DynamicConfigSectionDeleted, WotValidationConfig> {

    @Nullable
    @Override
    public WotValidationConfig handle(final DynamicConfigSectionDeleted event, @Nullable final WotValidationConfig entity,
            long revision) {
        if (entity == null) {
            return null;
        }

        final String scopeId = event.getScopeId();
        final List<DynamicValidationConfig> updatedConfigs = entity.getDynamicConfigs().stream()
                .filter(section -> !section.getScopeId().equals(scopeId))
                .toList();

        final WotValidationConfigId configId = entity.getConfigId();
        return WotValidationConfig.of(
                configId,
                entity.isEnabled().orElse(null),
                entity.logWarningInsteadOfFailingApiCalls().orElse(null),
                entity.getThingConfig().orElse(null),
                entity.getFeatureConfig().orElse(null),
                updatedConfigs,
                WotValidationConfigRevision.of(revision),
                entity.getCreated().orElse(null),
                entity.getModified().orElse(null),
                entity.isDeleted(),
                entity.getMetadata().orElse(null)
        );
    }
} 