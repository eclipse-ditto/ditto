/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;

/**
 * This strategy handles the {@link RetrieveAclEntry} command.
 */
@Immutable
final class RetrieveAclEntryStrategy extends AbstractCommandStrategy<RetrieveAclEntry> {

    /**
     * Constructs a new {@code RetrieveAclEntryStrategy} object.
     */
    RetrieveAclEntryStrategy() {
        super(RetrieveAclEntry.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAclEntry command) {
        final String thingId = context.getThingId();
        final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return getThingOrThrow(thing).getAccessControlList()
                .flatMap(acl -> acl.getEntryFor(authorizationSubject))
                .map(aclEntry -> ResultFactory.newResult(RetrieveAclEntryResponse.of(thingId, aclEntry, dittoHeaders)))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.aclEntryNotFound(thingId, authorizationSubject, dittoHeaders)));
    }

}
