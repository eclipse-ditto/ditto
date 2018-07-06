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
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link ModifyAclEntry} command.
 */
@NotThreadSafe
final class ModifyAclEntryStrategy extends AbstractCommandStrategy<ModifyAclEntry> {

    /**
     * Constructs a new {@code ModifyAclEntryStrategy} object.
     */
    ModifyAclEntryStrategy() {
        super(ModifyAclEntry.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final ModifyAclEntry command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.getNextRevision();
        final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
        final AclEntry modifiedAclEntry = command.getAclEntry();
        final Validator aclValidator = AclValidator.newInstance(acl.setEntry(modifiedAclEntry),
                Thing.MIN_REQUIRED_PERMISSIONS);
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (aclValidator.isValid()) {
            final ThingModifiedEvent eventToPersist;
            final ModifyAclEntryResponse response;

            if (acl.contains(modifiedAclEntry.getAuthorizationSubject())) {
                eventToPersist = AclEntryModified.of(command.getId(), modifiedAclEntry, nextRevision,
                        eventTimestamp(), dittoHeaders);
                response = ModifyAclEntryResponse.modified(thingId, modifiedAclEntry, dittoHeaders);
            } else {
                eventToPersist = AclEntryCreated.of(command.getId(), modifiedAclEntry, nextRevision,
                        eventTimestamp(), dittoHeaders);
                response = ModifyAclEntryResponse.created(thingId, modifiedAclEntry, dittoHeaders);
            }

            return newResult(eventToPersist, response);
        } else {
            return newResult(aclInvalid(thingId, aclValidator.getReason(), dittoHeaders.getAuthorizationContext(),
                    dittoHeaders));
        }
    }

}
