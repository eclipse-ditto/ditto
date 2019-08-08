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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;

/**
 * This strategy handles the {@link ModifyPolicyId} command.
 */
@Immutable
final class ModifyPolicyIdStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyPolicyId, String> {

    /**
     * Constructs a new {@code ModifyPolicyIdStrategy} object.
     */
    ModifyPolicyIdStrategy() {
        super(ModifyPolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyPolicyId command) {

        return extractPolicyId(thing)
                .map(policyId -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private Optional<String> extractPolicyId(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getPolicyId();
    }

    private Result getModifyResult(final Context context, final long nextRevision,
            final ModifyPolicyId command) {
        final ThingId thingId = context.getThingEntityId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                PolicyIdModified.of(thingId, command.getPolicyId(), nextRevision, getEventTimestamp(),
                        dittoHeaders),
                ModifyPolicyIdResponse.modified(thingId, dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision,
            final ModifyPolicyId command) {
        final ThingId thingId = context.getThingEntityId();
        final String policyId = command.getPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                PolicyIdCreated.of(thingId, policyId, nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyPolicyIdResponse.created(thingId, policyId, dittoHeaders), this);
    }

    @Override
    public Optional<String> determineETagEntity(final ModifyPolicyId command, @Nullable final Thing thing) {
        return extractPolicyId(thing);
    }
}
