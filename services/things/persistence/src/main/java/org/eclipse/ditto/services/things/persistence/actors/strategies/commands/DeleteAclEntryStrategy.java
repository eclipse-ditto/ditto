/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;

/**
 * This strategy handles the {@link DeleteAclEntry} command.
 */
@Immutable
final class DeleteAclEntryStrategy extends AbstractConditionalHeadersCheckingCommandStrategy<DeleteAclEntry, AclEntry> {

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

        return extractAcl(thing, command)
                .map(acl -> getDeleteAclEntryResult(acl, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.aclEntryNotFound(context.getThingId(),
                        authSubject, dittoHeaders)));
    }

    private Optional<AccessControlList> extractAcl(@Nullable final Thing thing, final DeleteAclEntry command) {
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();

        return getThingOrThrow(thing).getAccessControlList()
                .filter(acl -> acl.contains(authSubject));
    }

    private Result getDeleteAclEntryResult(final AccessControlList acl, final Context context,
            final long nextRevision, final DeleteAclEntry command) {

        final String thingId = context.getThingId();
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final AccessControlList aclWithoutAuthSubject = acl.removeAllPermissionsOf(authSubject);

        final Validator validator = getAclValidator(aclWithoutAuthSubject);
        if (!validator.isValid()) {
            return ResultFactory.newErrorResult(ExceptionFactory.aclInvalid(thingId, validator.getReason(), dittoHeaders));
        }

        return ResultFactory.newMutationResult(command,
                AclEntryDeleted.of(thingId, authSubject, nextRevision, getEventTimestamp(), dittoHeaders),
                DeleteAclEntryResponse.of(thingId, authSubject, dittoHeaders), this);
    }

    private static Validator getAclValidator(final AccessControlList acl) {
        return AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS);
    }

    @Override
    public Optional<AclEntry> determineETagEntity(final DeleteAclEntry command, @Nullable final Thing thing) {
        return extractAclEntry(thing, command);
    }

    private Optional<AclEntry> extractAclEntry(@Nullable final Thing thing, final DeleteAclEntry command) {
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();

        return getThingOrThrow(thing).getAccessControlList()
                .flatMap(acl -> acl.getEntryFor(authSubject));
    }
}
