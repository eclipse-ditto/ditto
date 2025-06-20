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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.RetrieveMergedWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveMergedWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * Strategy for handling the {@link RetrieveMergedWotValidationConfig} command.
 * <p>
 * This strategy retrieves a WoT validation configuration by its config ID and merges it with the static configuration.
 * The merged configuration represents the effective validation settings, with dynamic config taking precedence over static.
 * If the configuration does not exist, an error is returned.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
final class RetrieveMergedWotValidationConfigStrategy
        extends AbstractWotValidationConfigCommandStrategy<RetrieveMergedWotValidationConfig> {

    private final TmValidationConfig staticConfig;

    /**
     * Constructs a new {@code RetrieveMergedWotValidationConfigStrategy} object.
     *
     * @param staticConfig the static configuration from Helm.
     */
    RetrieveMergedWotValidationConfigStrategy(final TmValidationConfig staticConfig) {
        super(RetrieveMergedWotValidationConfig.class);
        this.staticConfig = staticConfig;
    }

    /**
     * Calculates relative metadata for the merged config. Always returns empty for this strategy.
     *
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param command the retrieve merged command.
     * @return always {@code Optional.empty()}.
     */
    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig entity,
            final RetrieveMergedWotValidationConfig command) {
        return Optional.empty();
    }

    /**
     * Applies the retrieve merged command to the current entity, merging it with the static config.
     *
     * @param context the command context.
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param nextRevision the next revision number.
     * @param command the retrieve merged command.
     * @param metadata optional metadata.
     * @return a successful result with the merged config if found, or an error result if not found.
     */
    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final CommandStrategy.Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final RetrieveMergedWotValidationConfig command,
            @Nullable final Metadata metadata) {
        if (entity == null) {
            final WotValidationConfigNotAccessibleException error =
                    WotValidationConfigNotAccessibleException.newBuilder(command.getEntityId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build();
            return ResultFactory.newErrorResult(error, command);
        }

        final WotValidationConfig mergedConfig = WotValidationConfigUtils.mergeConfigs(entity, staticConfig);

        final RetrieveMergedWotValidationConfigResponse response = RetrieveMergedWotValidationConfigResponse.of(
                mergedConfig, command.getDittoHeaders());
        return ResultFactory.newQueryResult(command, response);
    }

    @Override
    public boolean isDefined(final CommandStrategy.Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final RetrieveMergedWotValidationConfig command) {
        return true;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveMergedWotValidationConfig command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveMergedWotValidationConfig command,
            @Nullable final WotValidationConfig nextEntity) {
        return Optional.ofNullable(nextEntity).flatMap(EntityTag::fromEntity);
    }
} 