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
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
    protected Result<ThingEvent> doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteAclEntry command) {
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractAcl(thing, command)
                .map(acl -> getDeleteAclEntryResult(acl, context, nextRevision, command, thing))
                .orElseGet(
                        () -> ResultFactory.newErrorResult(ExceptionFactory.aclEntryNotFound(context.getThingEntityId(),
                                authSubject, dittoHeaders)));
    }

    private Optional<AccessControlList> extractAcl(@Nullable final Thing thing, final DeleteAclEntry command) {
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();

        return getEntityOrThrow(thing).getAccessControlList()
                .filter(acl -> acl.contains(authSubject));
    }

    private Result<ThingEvent> getDeleteAclEntryResult(final AccessControlList acl, final Context context,
            final long nextRevision, final DeleteAclEntry command, @Nullable final Thing thing) {

        final ThingId thingId = context.getThingEntityId();
        final AuthorizationSubject authSubject = command.getAuthorizationSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final AccessControlList aclWithoutAuthSubject = acl.removeAllPermissionsOf(authSubject);

        final Validator validator = getAclValidator(aclWithoutAuthSubject);
        if (!validator.isValid()) {
            return ResultFactory.newErrorResult(
                    ExceptionFactory.aclInvalid(thingId, validator.getReason(), dittoHeaders));
        }

        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteAclEntryResponse.of(thingId, authSubject, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                AclEntryDeleted.of(thingId, authSubject, nextRevision, getEventTimestamp(), dittoHeaders), response);
    }

    private static Validator getAclValidator(final AccessControlList acl) {
        return AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS);
    }

    @Override
    public Optional<AclEntry> determineETagEntity(final DeleteAclEntry command, @Nullable final Thing thing) {
        return Optional.empty();
    }

}
