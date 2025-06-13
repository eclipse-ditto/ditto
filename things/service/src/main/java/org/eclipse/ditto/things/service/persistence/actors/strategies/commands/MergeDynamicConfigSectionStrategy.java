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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.MergeDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigRunTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command strategy for handling {@link MergeDynamicConfigSection} commands.
 * <p>
 * This strategy merges (creates or updates) a dynamic config section in a WoT validation config entity, ensuring only one section per scope ID exists.
 * It emits a {@link DynamicConfigSectionMerged} event and updates the config in DData.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class MergeDynamicConfigSectionStrategy
        extends AbstractWotValidationConfigCommandStrategy<MergeDynamicConfigSection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeDynamicConfigSectionStrategy.class);
    private final WotValidationConfigDData ddata;

    MergeDynamicConfigSectionStrategy(final WotValidationConfigDData ddata) {
        super(MergeDynamicConfigSection.class);
        this.ddata = ddata;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final MergeDynamicConfigSection command) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final MergeDynamicConfigSection command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final MergeDynamicConfigSection command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final MergeDynamicConfigSection command,
            @Nullable final Metadata metadata) {
        final String scopeId = command.getScopeId();
        final DynamicValidationConfig mergeSection = command.getDynamicConfigSection();
        WotValidationConfig updatedEntity = entity;

        final List<DynamicValidationConfig> updatedDynamicConfig = entity.getDynamicConfigs().stream()
                .filter(section -> !section.getScopeId().equals(scopeId))
                .collect(java.util.stream.Collectors.toList());

        updatedDynamicConfig.add(mergeSection);

        updatedEntity = WotValidationConfig.of(
                entity.getConfigId(),
                entity.isEnabled().orElse(null),
                entity.logWarningInsteadOfFailingApiCalls().orElse(null),
                entity.getThingConfig().orElse(null),
                entity.getFeatureConfig().orElse(null),
                updatedDynamicConfig,
                entity.getRevision().orElse(null),
                entity.getCreated().orElse(null),
                entity.getModified().orElse(null),
                entity.isDeleted(),
                entity.getMetadata().orElse(null)
        );
        try {
            ddata.add(updatedEntity.toJson())
                    .thenRun(() -> LOGGER.debug("Successfully {} global config with dynamic section",
                            "updated"))
                    .exceptionally(error -> {
                        LOGGER.error("Failed to {} global config: {}",
                                "update",
                                error instanceof CompletionException ? error.getCause().getMessage() :
                                        error.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Error while {} global config: {}",
                    "updating", e.getMessage(), e);
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilderForScope(scopeId)
                            .description("Failed to " + ("update") +
                                    " WoT validation config: " + e.getMessage())
                            .dittoHeaders(command.getDittoHeaders())
                            .build(),
                    command
            );
        }

        final var event = DynamicConfigSectionMerged.of(
                command.getEntityId(),
                JsonPointer.of("/dynamicConfig/" + scopeId),
                mergeSection,
                nextRevision,
                Instant.now(),
                command.getDittoHeaders(),
                metadata
        );

        final ModifyWotValidationConfigResponse response = ModifyWotValidationConfigResponse.modified(
                command.getEntityId(),
                createCommandResponseDittoHeaders(command.getDittoHeaders(), nextRevision)
        );
        return ResultFactory.newMutationResult(command, event, response, false, false);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final MergeDynamicConfigSection command) {
        return true;
    }
} 