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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import javax.annotation.concurrent.NotThreadSafe;

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
@NotThreadSafe
public final class DeleteAclEntryStrategy extends AbstractThingCommandStrategy<DeleteAclEntry> {

    /**
     * Constructs a new {@code DeleteAclEntryStrategy} object.
     */
    public DeleteAclEntryStrategy() {
        super(DeleteAclEntry.class);
    }

    @Override
    protected Result doApply(final Context context, final DeleteAclEntry command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.nextRevision();
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
                return result(aclEntryDeleted, DeleteAclEntryResponse.of(thingId, authorizationSubject, dittoHeaders));
            } else {
                return result(aclInvalid(thingId, aclValidator.getReason(), dittoHeaders.getAuthorizationContext(),
                        dittoHeaders));
            }
        } else {
            return result(aclEntryNotFound(thingId, authorizationSubject, dittoHeaders));
        }
    }
}