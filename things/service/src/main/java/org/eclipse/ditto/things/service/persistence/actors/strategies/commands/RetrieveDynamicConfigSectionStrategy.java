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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.RetrieveDynamicConfigSection;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.signals.commands.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Optional;

/**
 * Strategy for handling {@link RetrieveDynamicConfigSection} commands.
 * <p>
 * This strategy retrieves a specific dynamic config section from a WoT validation configuration, identified by its scope ID.
 * If the section exists, it is returned as a response. If not, an error is returned.
 * </p>
 */
final class RetrieveDynamicConfigSectionStrategy extends AbstractWotValidationConfigCommandStrategy<RetrieveDynamicConfigSection> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveDynamicConfigSectionStrategy.class);

    /**
     * Constructs a new {@code RetrieveDynamicConfigSectionStrategy} object.
     */
    RetrieveDynamicConfigSectionStrategy() {
        super(RetrieveDynamicConfigSection.class);
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final RetrieveDynamicConfigSection command) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveDynamicConfigSection command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveDynamicConfigSection command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final RetrieveDynamicConfigSection command,
            @Nullable final Metadata metadata) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String scopeId = command.getScopeId();

        LOGGER.info("Received RetrieveDynamicConfigSection: scopeId={}", scopeId);

        if (entity == null) {
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilderForScope(scopeId)
                            .description("No WoT validation config found")
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final Optional<DynamicValidationConfig> section = entity.getDynamicConfigs().stream()
                .filter(s -> s.getScopeId().equals(scopeId))
                .findFirst();

        if (section.isEmpty()) {
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilderForScope(scopeId)
                            .description("Dynamic config section not found for scope: " + scopeId)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final JsonObject sectionJson = section.get().toJson();
        DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder()
                .putHeader(org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.ENTITY_REVISION.getKey(),
                        String.valueOf(entity.getRevision().get()));
        EntityTag.fromEntity(entity).ifPresent(builder::eTag);
        DittoHeaders headersWithRevisionAndEtag = builder.build();

        final RetrieveWotValidationConfigResponse response = RetrieveWotValidationConfigResponse.of(
                command.getEntityId(),
                sectionJson,
                headersWithRevisionAndEtag);

        return ResultFactory.newQueryResult(command, response);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final RetrieveDynamicConfigSection command) {
        return true;
    }
} 