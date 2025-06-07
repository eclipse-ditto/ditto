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
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;

/**
 * Strategy for handling the {@link RetrieveWotValidationConfig} command.
 * <p>
 * This strategy retrieves a WoT validation configuration by its config ID. If the configuration exists, it returns
 * the full config document as a response. If not, it returns an error indicating the config is not accessible.
 * </p>
 */
@Immutable
final class RetrieveWotValidationConfigStrategy extends AbstractWotValidationConfigCommandStrategy<RetrieveWotValidationConfig> {

    /**
     * Constructs a new {@code RetrieveWotValidationConfigStrategy} object.
     */
    RetrieveWotValidationConfigStrategy() {
        super(RetrieveWotValidationConfig.class);
    }

    /**
     * Calculates relative metadata for the retrieved config. Always returns empty for this strategy.
     *
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param command the retrieve command.
     * @return always {@code Optional.empty()}.
     */
    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig entity,
            final RetrieveWotValidationConfig command) {
        return Optional.empty();
    }

    /**
     * Applies the retrieve command to the current entity.
     *
     * @param context the command context.
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param nextRevision the next revision number.
     * @param command the retrieve command.
     * @param metadata optional metadata.
     * @return a successful result with the config if found, or an error result if not found.
     */
    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final CommandStrategy.Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final long nextRevision,
            final RetrieveWotValidationConfig command,
            @Nullable final Metadata metadata) {

        if (entity == null) {
            return ResultFactory.newErrorResult(
                    WotValidationConfigNotAccessibleException.newBuilder(command.getEntityId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build(),
                    command);
        }

        DittoHeadersBuilder<?, ?> builder = command.getDittoHeaders().toBuilder()
                .putHeader(org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.ENTITY_REVISION.getKey(), String.valueOf(entity.getRevision().get()));
        EntityTag.fromEntity(entity).ifPresent(builder::eTag);
        DittoHeaders headersWithRevisionAndEtag = builder.build();

        return ResultFactory.newQueryResult(command,
                RetrieveWotValidationConfigResponse.of(
                        WotValidationConfigId.of(command.getEntityId().toString()),
                        entity.toJson(),
                        headersWithRevisionAndEtag));
    }

    @Override
    public boolean isDefined(final CommandStrategy.Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final RetrieveWotValidationConfig command) {
        return true;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveWotValidationConfig command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveWotValidationConfig command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}