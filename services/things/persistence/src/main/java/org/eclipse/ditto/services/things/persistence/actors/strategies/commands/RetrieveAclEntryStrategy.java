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

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link RetrieveAclEntry} command.
 */
@Immutable
final class RetrieveAclEntryStrategy extends AbstractThingCommandStrategy<RetrieveAclEntry> {

    /**
     * Constructs a new {@code RetrieveAclEntryStrategy} object.
     */
    RetrieveAclEntryStrategy() {
        super(RetrieveAclEntry.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAclEntry command) {
        final ThingId thingId = context.getState();
        final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractAclEntry(command, thing)
                .map(aclEntry -> {
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            RetrieveAclEntryResponse.of(thingId, aclEntry, dittoHeaders), thing);
                    return ResultFactory.<ThingEvent>newQueryResult(command, response);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.aclEntryNotFound(thingId, authorizationSubject, dittoHeaders)));
    }

    private Optional<AclEntry> extractAclEntry(final RetrieveAclEntry command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getAccessControlList()
                .flatMap(acl -> acl.getEntryFor(command.getAuthorizationSubject()));
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveAclEntry command, @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveAclEntry command, @Nullable final Thing newEntity) {
        return extractAclEntry(command, newEntity).flatMap(EntityTag::fromEntity);
    }
}
