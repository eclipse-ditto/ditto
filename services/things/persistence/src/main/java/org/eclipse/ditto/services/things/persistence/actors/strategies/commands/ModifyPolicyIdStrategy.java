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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;

/**
 * This strategy handles the {@link ModifyPolicyId} command.
 */
@Immutable
final class ModifyPolicyIdStrategy extends AbstractCommandStrategy<ModifyPolicyId> {

    /**
     * Constructs a new {@code ModifyPolicyIdStrategy} object.
     */
    ModifyPolicyIdStrategy() {
        super(ModifyPolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyPolicyId command) {

        return getThingOrThrow(thing).getPolicyId()
                .map(policyId -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private static Result getModifyResult(final Context context, final long nextRevision, final ModifyPolicyId command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                PolicyIdModified.of(thingId, command.getPolicyId(), nextRevision, getEventTimestamp(),
                        dittoHeaders),
                ModifyPolicyIdResponse.modified(thingId, dittoHeaders));
    }

    private static Result getCreateResult(final Context context, final long nextRevision, final ModifyPolicyId command) {
        final String thingId = context.getThingId();
        final String policyId = command.getPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                PolicyIdCreated.of(thingId, policyId, nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyPolicyIdResponse.created(thingId, policyId, dittoHeaders));
    }

}
