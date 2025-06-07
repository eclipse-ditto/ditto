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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfig;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling the {@link org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfig} command.
 * <p>
 * This strategy deletes a WoT validation configuration by its config ID. If the configuration exists, it is removed
 * from the distributed data store and a deletion event is emitted. If not, an error is returned.
 * </p>
 */
@Immutable
final class DeleteWotValidationConfigStrategy extends AbstractWotValidationConfigCommandStrategy<DeleteWotValidationConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteWotValidationConfigStrategy.class);
    private final WotValidationConfigDData ddata;

    /**
     * Constructs a new {@code DeleteWotValidationConfigStrategy} object.
     *
     * @param ddata the DData instance for WoT validation configs.
     */
    DeleteWotValidationConfigStrategy(
            final WotValidationConfigDData ddata) {
        super(DeleteWotValidationConfig.class);
        this.ddata = ddata;
    }

    /**
     * Calculates relative metadata for the deleted config. Always returns empty for this strategy.
     *
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param command the delete command.
     * @return always {@code Optional.empty()}.
     */
    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final WotValidationConfig entity,
            final DeleteWotValidationConfig command) {
        return Optional.empty();
    }

    /**
     * Applies the delete command to the current entity, removing it from the distributed data store.
     *
     * @param context the command context.
     * @param entity the current WoT validation config entity, or {@code null} if not found.
     * @param nextRevision the next revision number.
     * @param command the delete command.
     * @param metadata optional metadata.
     * @return a successful result with the deletion event if found, or an error result if not found.
     */
    @Override
    protected Result<WotValidationConfigEvent<?>> doApply(final Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity, final long nextRevision,
            final DeleteWotValidationConfig command, @Nullable final Metadata metadata) {

        if (entity == null) {
            LOGGER.debug("WoT validation config with ID <{}> does not exist", command.getEntityId());
            return ResultFactory.newErrorResult(
                    new WotValidationConfigNotAccessibleException(command.getEntityId(), command.getDittoHeaders()),
                    command);
        }

        final WotValidationConfigId configId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Instant timestamp = getEventTimestamp();

        final WotValidationConfigDeleted event = WotValidationConfigDeleted.of(
                configId,
                nextRevision,
                timestamp,
                dittoHeaders,
                metadata
        );

        LOGGER.info("Initiating DData removal for WoT validation config with ID <{}>", configId);
        
        final DeleteWotValidationConfigResponse response = DeleteWotValidationConfigResponse.of(configId, dittoHeaders);

        ddata.clear().thenRun(() -> {
            LOGGER.info("Successfully cleared DData for WoT validation config with ID <{}>", configId);
        });

        return ResultFactory.newMutationResult(command, event, response, false, true);
    }

    @Override
    public boolean isDefined(final CommandStrategy.Context<WotValidationConfigId> context,
            @Nullable final WotValidationConfig entity,
            final DeleteWotValidationConfig command) {
        return command.getEntityId().toString().length() > 0;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteWotValidationConfig command,
            @Nullable final WotValidationConfig previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteWotValidationConfig command,
            @Nullable final WotValidationConfig newEntity) {
        return Optional.empty();
    }
}