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
import java.util.Optional;

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

/**
 * Command strategy for handling {@link MergeDynamicConfigSection} commands.
 * <p>
 * This strategy merges (creates or updates) a dynamic config section in a WoT validation config entity, ensuring only one section per scope ID exists.
 * It emits a {@link DynamicConfigSectionMerged} event; the resulting entity state is published to the distributed
 * data by the {@code WotValidationConfigPersistenceActor}.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class MergeDynamicConfigSectionStrategy
        extends AbstractWotValidationConfigCommandStrategy<MergeDynamicConfigSection> {


    MergeDynamicConfigSectionStrategy() {
        super(MergeDynamicConfigSection.class);
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