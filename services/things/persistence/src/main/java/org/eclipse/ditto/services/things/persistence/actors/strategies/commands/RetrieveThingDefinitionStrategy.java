/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition} command.
 */
@Immutable
final class RetrieveThingDefinitionStrategy extends AbstractThingCommandStrategy<RetrieveThingDefinition> {

    /**
     * Constructs a new {@code RetrieveThingDefinitionStrategy} object.
     */
    RetrieveThingDefinitionStrategy() {
        super(RetrieveThingDefinition.class);
    }


    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveThingDefinition command) {

        return extractDefinition(thing)
                .map(definition -> RetrieveThingDefinitionResponse.of(context.getState(), definition,
                        command.getDittoHeaders()))
                .<Result<ThingEvent>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ThingDefinitionNotAccessibleException.newBuilder(context.getState())
                                .dittoHeaders(command.getDittoHeaders())
                                .build()));
    }

    private Optional<ThingDefinition> extractDefinition(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getDefinition();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveThingDefinition command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveThingDefinition command, @Nullable final Thing newEntity) {
        return extractDefinition(newEntity).flatMap(EntityTag::fromEntity);
    }
}