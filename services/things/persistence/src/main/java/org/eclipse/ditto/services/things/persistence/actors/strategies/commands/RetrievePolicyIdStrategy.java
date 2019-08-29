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

import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;

/**
 * This strategy handles the {@link RetrievePolicyId} command.
 */
@Immutable
final class RetrievePolicyIdStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrievePolicyId, PolicyId> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrievePolicyId command) {

        return extractPolicyId(thing)
                .map(policyId -> RetrievePolicyIdResponse.of(context.getThingEntityId(), policyId,
                        command.getDittoHeaders()))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        PolicyIdNotAccessibleException.newBuilder(context.getThingEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build()));
    }

    private Optional<PolicyId> extractPolicyId(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getPolicyEntityId();
    }

    @Override
    public Optional<PolicyId> determineETagEntity(final RetrievePolicyId command, @Nullable final Thing thing) {
        return extractPolicyId(thing);
    }
}
