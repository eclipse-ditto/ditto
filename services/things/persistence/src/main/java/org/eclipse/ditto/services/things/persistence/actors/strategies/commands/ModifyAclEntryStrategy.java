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

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link ModifyAclEntry} command.
 */
@Immutable
final class ModifyAclEntryStrategy extends AbstractThingCommandStrategy<ModifyAclEntry> {

    /**
     * Constructs a new {@code ModifyAclEntryStrategy} object.
     */
    ModifyAclEntryStrategy() {
        super(ModifyAclEntry.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAclEntry command) {

        final AccessControlList acl =
                getEntityOrThrow(thing).getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);

        final AccessControlList modifiedAcl = acl.setEntry(command.getAclEntry());
        final Validator validator = getAclValidator(modifiedAcl);
        if (!validator.isValid()) {
            return ResultFactory.newErrorResult(
                    ExceptionFactory.aclInvalid(context.getState(), validator.getReason(),
                            command.getDittoHeaders()));
        }

        return getModifyOrCreateResult(acl, context, nextRevision, command, thing);
    }

    private static Validator getAclValidator(final AccessControlList acl) {
        return AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS);
    }

    private Result<ThingEvent> getModifyOrCreateResult(final AccessControlList acl, final Context<ThingId> context,
            final long nextRevision, final ModifyAclEntry command, @Nullable Thing thing) {

        final AclEntry aclEntry = command.getAclEntry();
        if (acl.contains(aclEntry.getAuthorizationSubject())) {
            return getModifyResult(context, nextRevision, command, thing);
        }
        return getCreateResult(context, nextRevision, command, thing);
    }

    private Result<ThingEvent> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAclEntry command, @Nullable Thing thing) {
        final ThingId thingId = context.getState();
        final AclEntry aclEntry = command.getAclEntry();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AclEntryModified.of(thingId, aclEntry, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAclEntryResponse.modified(thingId, aclEntry, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAclEntry command, @Nullable Thing thing) {
        final ThingId thingId = context.getState();
        final AclEntry aclEntry = command.getAclEntry();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AclEntryCreated.of(thingId, aclEntry, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAclEntryResponse.created(thingId, aclEntry, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyAclEntry command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(Thing::getAccessControlList)
                .flatMap(acl -> acl.getEntryFor(command.getAclEntry().getAuthorizationSubject()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyAclEntry command, @Nullable final Thing newEntity) {
        return Optional.of(command.getAclEntry()).flatMap(EntityTag::fromEntity);
    }
}
