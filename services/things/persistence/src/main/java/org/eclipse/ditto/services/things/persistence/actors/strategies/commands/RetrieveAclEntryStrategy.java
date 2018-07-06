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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;

/**
 * This strategy handles the {@link RetrieveAclEntry} command.
 */
@NotThreadSafe
final class RetrieveAclEntryStrategy extends AbstractCommandStrategy<RetrieveAclEntry> {

    /**
     * Constructs a new {@code RetrieveAclEntryStrategy} object.
     */
    RetrieveAclEntryStrategy() {
        super(RetrieveAclEntry.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveAclEntry command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
        final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (acl.contains(authorizationSubject)) {
            final AclEntry aclEntry = acl.getEntryFor(authorizationSubject)
                    .orElseGet(
                            () -> AclEntry.newInstance(authorizationSubject, ThingsModelFactory.noPermissions()));
            return newResult(RetrieveAclEntryResponse.of(thingId, aclEntry, dittoHeaders));
        } else {
            return newResult(aclEntryNotFound(thingId, authorizationSubject, dittoHeaders));
        }
    }

}
