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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;

/**
 * This strategy handles the {@link DeleteAclEntry} command.
 */
@Immutable
final class DeleteAclEntryStrategy extends AbstractCommandStrategy<DeleteAclEntry> {

    /**
     * Constructs a new {@code DeleteAclEntryStrategy} object.
     */
    DeleteAclEntryStrategy() {
        super(DeleteAclEntry.class);
    }

    @Override
    protected Result doApply(final Context context, final Thing thing,
            final long nextRevision, final DeleteAclEntry command) {
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return getThingOrThrow(thing).getAccessControlList()
                .filter(acl -> acl.contains(authSubject))
                .map(acl -> getDeleteAclEntryResult(acl, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newResult(ExceptionFactory.aclEntryNotFound(context.getThingId(),
                        authSubject, dittoHeaders)));
    }

    private static Result getDeleteAclEntryResult(final AccessControlList acl, final Context context,
            final long nextRevision, final DeleteAclEntry command) {

        final String thingId = context.getThingId();
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final AccessControlList aclWithoutAuthSubject = acl.removeAllPermissionsOf(authSubject);

        final Validator validator = getAclValidator(aclWithoutAuthSubject);
        if (!validator.isValid()) {
            return ResultFactory.newResult(ExceptionFactory.aclInvalid(thingId, validator.getReason(), dittoHeaders));
        }

        return ResultFactory.newResult(
                AclEntryDeleted.of(thingId, authSubject, nextRevision, getEventTimestamp(), dittoHeaders),
                DeleteAclEntryResponse.of(thingId, authSubject, dittoHeaders));
    }

    private static Validator getAclValidator(final AccessControlList acl) {
        return AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS);
    }

}
