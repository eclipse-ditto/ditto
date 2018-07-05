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

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link ModifyPolicyId} command.
 */
@NotThreadSafe
final class ModifyPolicyIdStrategy extends AbstractThingCommandStrategy<ModifyPolicyId> {

    /**
     * Constructs a new {@code ModifyPolicyIdStrategy} object.
     */
    public ModifyPolicyIdStrategy() {
        super(ModifyPolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyPolicyId command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.nextRevision();
        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        if (thing.getPolicyId().isPresent()) {
            eventToPersist = PolicyIdModified.of(thingId, command.getPolicyId(), nextRevision, eventTimestamp(),
                    command.getDittoHeaders());
            response = ModifyPolicyIdResponse.modified(thingId, command.getDittoHeaders());
        } else {
            eventToPersist = PolicyIdCreated.of(thingId, command.getPolicyId(), nextRevision, eventTimestamp(),
                    command.getDittoHeaders());
            response = ModifyPolicyIdResponse.created(thingId, command.getPolicyId(), command.getDittoHeaders());
        }

        return result(eventToPersist, response);
    }
}