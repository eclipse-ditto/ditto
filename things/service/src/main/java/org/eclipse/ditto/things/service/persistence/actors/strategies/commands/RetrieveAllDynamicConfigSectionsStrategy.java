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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.RetrieveAllDynamicConfigSections;
import org.eclipse.ditto.things.model.devops.commands.RetrieveAllDynamicConfigSectionsResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling {@link org.eclipse.ditto.things.model.devops.commands.RetrieveAllDynamicConfigSections} commands.
 * <p>
 * This strategy retrieves all dynamic config sections from a WoT validation configuration. Each section contains
 * validation settings that can be overridden for a specific scope. The strategy returns all sections as a response.
 * </p>
 */
final class RetrieveAllDynamicConfigSectionsStrategy extends AbstractWotValidationConfigCommandStrategy<RetrieveAllDynamicConfigSections> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveAllDynamicConfigSectionsStrategy.class);

    RetrieveAllDynamicConfigSectionsStrategy() {
        super(RetrieveAllDynamicConfigSections.class);
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final RetrieveAllDynamicConfigSections command) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveAllDynamicConfigSections command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveAllDynamicConfigSections command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final RetrieveAllDynamicConfigSections command,
            @Nullable final Metadata metadata) {
        LOGGER.info("Received RetrieveAllDynamicConfigSections");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        JsonArray dynamicConfigs;
        if (entity != null && entity.getDynamicConfigs() != null) {
            JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
            entity.getDynamicConfigs().forEach(section -> arrayBuilder.add(section.toJson()));
            dynamicConfigs = arrayBuilder.build();
        } else {
            dynamicConfigs = JsonFactory.newArrayBuilder().build();
        }

        DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder();
        if (entity != null) {
            builder.putHeader(org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.ENTITY_REVISION.getKey(),
                    String.valueOf(entity.getRevision().get()));
            EntityTag.fromEntity(entity).ifPresent(builder::eTag);
        }
        DittoHeaders headersWithRevisionAndEtag = builder.build();

        RetrieveAllDynamicConfigSectionsResponse response = RetrieveAllDynamicConfigSectionsResponse.of(
                command.getEntityId(),
                dynamicConfigs,
                headersWithRevisionAndEtag);
        return ResultFactory.newQueryResult(command, response);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final RetrieveAllDynamicConfigSections command) {
        return true;
    }
} 