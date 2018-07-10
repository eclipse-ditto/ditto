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

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;

/**
 * This strategy handles the {@link DeleteAclEntry} command.
 */
@ThreadSafe
public final class DeleteAclEntryStrategy extends AbstractCommandStrategy<DeleteAclEntry> {

    /**
     * Constructs a new {@code DeleteAclEntryStrategy} object.
     */
    DeleteAclEntryStrategy() {
        super(DeleteAclEntry.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final DeleteAclEntry command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.getNextRevision();
        final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
        final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        if (acl.contains(authorizationSubject)) {
            final Validator aclValidator =
                    AclValidator.newInstance(acl.removeAllPermissionsOf(authorizationSubject),
                            Thing.MIN_REQUIRED_PERMISSIONS);
            if (aclValidator.isValid()) {
                final AclEntryDeleted aclEntryDeleted =
                        AclEntryDeleted.of(thingId, authorizationSubject, nextRevision, eventTimestamp(), dittoHeaders);
                return newResult(aclEntryDeleted,
                        DeleteAclEntryResponse.of(thingId, authorizationSubject, dittoHeaders));
            } else {
                return newResult(aclInvalid(thingId, aclValidator.getReason(), dittoHeaders));
            }
        } else {
            return newResult(aclEntryNotFound(thingId, authorizationSubject, dittoHeaders));
        }
    }
}