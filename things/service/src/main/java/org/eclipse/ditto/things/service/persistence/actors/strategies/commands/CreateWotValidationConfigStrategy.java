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
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.CreateWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.CreateWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigRunTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

/**
 * Strategy for handling {@link CreateWotValidationConfig} commands.
 * <p>
 * This strategy creates a new WoT validation config. If the config already exists, it returns an error.
 * </p>
 *
 * @since 3.8.0
 */
final class CreateWotValidationConfigStrategy extends AbstractWotValidationConfigCommandStrategy<CreateWotValidationConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateWotValidationConfigStrategy.class);
    private final WotValidationConfigDData ddata;

    CreateWotValidationConfigStrategy(final WotValidationConfigDData ddata) {
        super(CreateWotValidationConfig.class);
        this.ddata = ddata;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final CreateWotValidationConfig command) {
        return Optional.empty();
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final CreateWotValidationConfig command,
            @Nullable final Metadata metadata) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Instant now = Instant.now();

        if (entity != null) {
            LOGGER.warn("WoT validation config already exists for id: {}", command.getEntityId());
            return ResultFactory.newErrorResult(
                    WotValidationConfigRunTimeException.newBuilder()
                            .description("WoT validation config already exists")
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command
            );
        }

        final WotValidationConfig newConfig = command.getValidationConfig();

        final WotValidationConfig configWithRevision = WotValidationConfig.of(
                command.getEntityId(),
                newConfig.isEnabled().orElse(null),
                newConfig.logWarningInsteadOfFailingApiCalls().orElse(null),
                newConfig.getThingConfig().orElse(null),
                newConfig.getFeatureConfig().orElse(null),
                newConfig.getDynamicConfigs(),
                WotValidationConfigRevision.of(nextRevision),
                now,
                now,
                false,
                metadata
        );

        final WotValidationConfigEvent<?> event = WotValidationConfigCreated.of(
                command.getEntityId(),
                configWithRevision,
                nextRevision,
                now,
                dittoHeaders,
                metadata
        );

        ddata.add(configWithRevision.toJson())
                .thenRun(() -> LOGGER.info("Successfully created WoT validation config for id: {}", command.getEntityId()))
                .exceptionally(error -> {
                    LOGGER.error("Failed to create WoT validation config: {}", error.getMessage());
                    return null;
                });

        DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder()
                .putHeader(org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.ENTITY_REVISION.getKey(),
                        String.valueOf(nextRevision));
        EntityTag.fromEntity(configWithRevision).ifPresent(builder::eTag);
        DittoHeaders headersWithRevisionAndEtag = builder.build();

        final CreateWotValidationConfigResponse response =
                CreateWotValidationConfigResponse.of(
                        command.getEntityId(),
                        configWithRevision.toJson(),
                        headersWithRevisionAndEtag
                );

        return ResultFactory.newMutationResult(command, event, response, true, false);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final CreateWotValidationConfig command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CreateWotValidationConfig command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context, @Nullable final WotValidationConfig entity, final CreateWotValidationConfig command) {
        return entity == null;
    }
}