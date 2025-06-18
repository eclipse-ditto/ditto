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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.DeleteDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigErrorException;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling the {@link org.eclipse.ditto.things.model.devops.commands.DeleteDynamicConfigSection} command.
 * <p>
 * This strategy deletes a specific dynamic config section from a WoT validation configuration, identified by its scope ID.
 * If the section exists, it is removed and a modification event is emitted. If not, an error is returned.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class DeleteDynamicConfigSectionStrategy
        extends AbstractWotValidationConfigCommandStrategy<DeleteDynamicConfigSection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteDynamicConfigSectionStrategy.class);

    private final WotValidationConfigDData ddata;

    /**
     * Constructs a new {@code DeleteDynamicConfigSectionStrategy} object.
     *
     * @param ddata the DData instance for WoT validation configs.
     */
    DeleteDynamicConfigSectionStrategy(final WotValidationConfigDData ddata) {
        super(DeleteDynamicConfigSection.class);
        this.ddata = ddata;
    }

    /**
     * Calculates relative metadata for the deleted dynamic config section. Always returns empty for this strategy.
     *
     * @param previousEntity the current WoT validation config entity, or {@code null} if not found.
     * @param command the delete dynamic config section command.
     * @return always {@code Optional.empty()}.
     */
    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final DeleteDynamicConfigSection command) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteDynamicConfigSection command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteDynamicConfigSection command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }


    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final DeleteDynamicConfigSection command,
            @Nullable final Metadata metadata) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String scopeId = command.getScopeId();
        final Instant now = Instant.now();

        LOGGER.info("Received DeleteDynamicConfigSection command for scopeId={}", scopeId);

        if (entity == null) {
            final String errorMessage = "No WoT validation config found";
            LOGGER.error(errorMessage);
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilder(command.getEntityId())
                            .description(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final boolean sectionExists = entity.getDynamicConfigs().stream()
                .anyMatch(section -> section.getScopeId().equals(scopeId));

        if (!sectionExists) {
            final String errorMessage = "Dynamic config section not found for scope: " + scopeId;
            LOGGER.warn(errorMessage);
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilderForScope(scopeId)
                            .description(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final List<DynamicValidationConfig> updatedDynamicConfig = entity.getDynamicConfigs().stream()
                .filter(section -> !section.getScopeId().equals(scopeId))
                .toList();

        final WotValidationConfig configWithMetadata = createWotValidationConfig(
                entity,
                updatedDynamicConfig,
                WotValidationConfigRevision.of(nextRevision),
                now,
                metadata);

        final DynamicConfigSectionDeleted event = DynamicConfigSectionDeleted.of(
                command.getEntityId(),
                command.getResourcePath(),
                scopeId,
                nextRevision,
                now,
                dittoHeaders,
                metadata
        );

        try {
            ddata.add(configWithMetadata.toJson())
                    .thenRun(() -> LOGGER.debug(
                            "Successfully updated DData after deleting dynamic config section for scopeId={}", scopeId))
                    .exceptionally(error -> {
                        final String errorMessage = error instanceof CompletionException ?
                                error.getCause().getMessage() : error.getMessage();
                        LOGGER.warn("Failed to update DData after deleting dynamic config section for scopeId={}: {}",
                                scopeId, errorMessage);
                        return null;
                    });
        } catch (final Exception e) {
            final String errorMessage = "Failed to update WoT validation config: " + e.getMessage();
            LOGGER.error(errorMessage, e);
            return ResultFactory.newErrorResult(
                    WotValidationConfigErrorException.newBuilder()
                            .description(errorMessage)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final DeleteWotValidationConfigResponse response = DeleteWotValidationConfigResponse.of(command.getEntityId(),
                dittoHeaders);

        LOGGER.info("Successfully deleted dynamic config section for scopeId={}", scopeId);
        return ResultFactory.newMutationResult(command, event, response, false, true);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final DeleteDynamicConfigSection command) {
        return true;
    }

    private static WotValidationConfig createWotValidationConfig(
            final WotValidationConfig entity,
            final List<DynamicValidationConfig> updatedDynamicConfig,
            final WotValidationConfigRevision nextRevision,
            final Instant now,
            @Nullable final Metadata metadata) {
        final WotValidationConfigId entityId = entity.getConfigId();

        return WotValidationConfig.of(
                entityId,
                entity.isEnabled().orElse(null),
                entity.logWarningInsteadOfFailingApiCalls().orElse(null),
                entity.getThingConfig().orElse(null),
                entity.getFeatureConfig().orElse(null),
                updatedDynamicConfig,
                nextRevision,
                entity.getCreated().orElseThrow(() -> new IllegalStateException("Created timestamp is required")),
                now,
                entity.isDeleted(),
                metadata
        );
    }
} 