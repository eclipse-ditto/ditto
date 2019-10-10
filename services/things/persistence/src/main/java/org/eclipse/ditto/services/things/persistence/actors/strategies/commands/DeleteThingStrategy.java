/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This strategy handles the {@link DeleteThing} command.
 */
@Immutable
final class DeleteThingStrategy extends AbstractThingCommandStrategy<DeleteThing> {

    /**
     * Constructs a new {@code DeleteThingStrategy} object.
     */
    DeleteThingStrategy() {
        super(DeleteThing.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteThing command) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final DiagnosticLoggingAdapter log = context.getLog();
        LogUtil.enhanceLogWithCorrelationId(log, command);
        log.info("Deleted Thing with ID <{}>.", thingId);

        final ThingEvent event = ThingDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response =
                appendETagHeaderIfProvided(command, DeleteThingResponse.of(thingId, dittoHeaders), null);

        return ResultFactory.newMutationResult(command, event, response, false, true);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteThing command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteThing command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
