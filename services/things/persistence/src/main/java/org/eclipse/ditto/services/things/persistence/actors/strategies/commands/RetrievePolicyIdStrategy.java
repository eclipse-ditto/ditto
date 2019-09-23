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
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link RetrievePolicyId} command.
 */
@Immutable
final class RetrievePolicyIdStrategy extends AbstractThingCommandStrategy<RetrievePolicyId> {

    /**
     * Constructs a new {@code RetrievePolicyIdStrategy} object.
     */
    RetrievePolicyIdStrategy() {
        super(RetrievePolicyId.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrievePolicyId command) {

        return extractPolicyId(thing)
                .map(policyId -> RetrievePolicyIdResponse.of(context.getState(), policyId,
                        command.getDittoHeaders()))
                .<Result<ThingEvent>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        PolicyIdNotAccessibleException.newBuilder(context.getState())
                                .dittoHeaders(command.getDittoHeaders())
                                .build()));
    }

    private Optional<PolicyId> extractPolicyId(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getPolicyEntityId();
    }

    @Override
    public Optional<?> previousETagEntity(final RetrievePolicyId command, @Nullable final Thing previousEntity) {
        return nextETagEntity(command, previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final RetrievePolicyId command, @Nullable final Thing newEntity) {
        return extractPolicyId(newEntity);
    }
}
