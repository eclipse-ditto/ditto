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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryModified;

/**
 * This strategy handles the {@link ModifyAclEntry} command.
 */
@Immutable
final class ModifyAclEntryStrategy extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyAclEntry, AclEntry> {

    /**
     * Constructs a new {@code ModifyAclEntryStrategy} object.
     */
    ModifyAclEntryStrategy() {
        super(ModifyAclEntry.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAclEntry command) {

        final AccessControlList acl =
                getThingOrThrow(thing).getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);

        final AccessControlList modifiedAcl = acl.setEntry(command.getAclEntry());
        final Validator validator = getAclValidator(modifiedAcl);
        if (!validator.isValid()) {
            return ResultFactory.newErrorResult(ExceptionFactory.aclInvalid(context.getThingId(), validator.getReason(),
                    command.getDittoHeaders()));
        }

        return getModifyOrCreateResult(acl, context, nextRevision, command);
    }

    private static Validator getAclValidator(final AccessControlList acl) {
        return AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS);
    }

    private Result getModifyOrCreateResult(final AccessControlList acl, final Context context,
            final long nextRevision, final ModifyAclEntry command) {

        final AclEntry aclEntry = command.getAclEntry();
        if (acl.contains(aclEntry.getAuthorizationSubject())) {
            return getModifyResult(context, nextRevision, command);
        }
        return getCreateResult(context, nextRevision, command);
    }

    private Result getModifyResult(final Context context, final long nextRevision,
            final ModifyAclEntry command) {
        final String thingId = context.getThingId();
        final AclEntry aclEntry = command.getAclEntry();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AclEntryModified.of(thingId, aclEntry, nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyAclEntryResponse.modified(thingId, aclEntry, dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision,
            final ModifyAclEntry command) {
        final String thingId = context.getThingId();
        final AclEntry aclEntry = command.getAclEntry();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AclEntryCreated.of(thingId, aclEntry, nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyAclEntryResponse.created(thingId, aclEntry, dittoHeaders), this);
    }

    @Override
    public Optional<AclEntry> determineETagEntity(final ModifyAclEntry command, @Nullable final Thing thing) {
        return extractAclEntry(command, thing);
    }

    private Optional<AclEntry> extractAclEntry(final ModifyAclEntry command, @Nullable final Thing thing) {
        return getThingOrThrow(thing).getAccessControlList()
                .flatMap(acl -> acl.getEntryFor(command.getAclEntry().getAuthorizationSubject()));
    }
}
