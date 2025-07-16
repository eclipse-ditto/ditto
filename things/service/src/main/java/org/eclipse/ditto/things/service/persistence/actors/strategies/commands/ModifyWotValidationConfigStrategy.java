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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling {@link ModifyWotValidationConfig} commands.
 *
 * @since 3.8.0
 */
final class ModifyWotValidationConfigStrategy
        extends AbstractWotValidationConfigCommandStrategy<ModifyWotValidationConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModifyWotValidationConfigStrategy.class);

    private final WotValidationConfigDData ddata;

    ModifyWotValidationConfigStrategy(final WotValidationConfigDData ddata) {
        super(ModifyWotValidationConfig.class);
        this.ddata = ddata;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig previousEntity,
            final ModifyWotValidationConfig command) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyWotValidationConfig command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyWotValidationConfig command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final ModifyWotValidationConfig command,
            @Nullable final Metadata metadata) {
        final WotValidationConfig inputConfig = command.getValidationConfig();

        final Instant now = Instant.now();
        final WotValidationConfig configWithRevision = WotValidationConfig.of(
                inputConfig.getConfigId(),
                inputConfig.isEnabled().orElse(null),
                inputConfig.logWarningInsteadOfFailingApiCalls().orElse(null),
                inputConfig.getThingConfig().orElse(null),
                inputConfig.getFeatureConfig().orElse(null),
                inputConfig.getDynamicConfigs(),
                WotValidationConfigRevision.of(nextRevision),
                (entity != null ? entity.getCreated().orElse(now) : now),
                now,
                inputConfig.isDeleted(),
                metadata
        );

        final WotValidationConfigEvent<?> event = WotValidationConfigModified.of(
                command.getEntityId(),
                configWithRevision,
                nextRevision,
                now,
                command.getDittoHeaders(),
                metadata
        );

        ddata.add(configWithRevision.toJson())
                .thenRun(() -> LOGGER.info("Successfully updated DData with merged config for <{}>",
                        command.getEntityId()))
                .exceptionally(error -> {
                    LOGGER.error("Failed to update DData for <{}>: {}", command.getEntityId(), error.getMessage());
                    return null;
                });

        final ModifyWotValidationConfigResponse response = ModifyWotValidationConfigResponse.modified(
                command.getEntityId(),
                createCommandResponseDittoHeaders(command.getDittoHeaders(), nextRevision));

        final WithDittoHeaders responseWithEtag = EntityTag.fromEntity(configWithRevision)
                .map(etag -> response.setDittoHeaders(
                        response.getDittoHeaders().toBuilder()
                                .eTag(etag)
                                .build()))
                .orElse(response);


        return ResultFactory.newMutationResult(command, event, responseWithEtag, false, false);
    }

    @Override
    public boolean isDefined(final Context<WotValidationConfigId> context, @Nullable final WotValidationConfig entity,
            final ModifyWotValidationConfig command) {
        return true;
    }
}