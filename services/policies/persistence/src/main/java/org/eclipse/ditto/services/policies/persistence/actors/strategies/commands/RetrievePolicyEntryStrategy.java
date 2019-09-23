/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry} command.
 */
final class RetrievePolicyEntryStrategy extends
        AbstractPolicyQueryCommandStrategy<RetrievePolicyEntry> {

    RetrievePolicyEntryStrategy() {
        super(RetrievePolicyEntry.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final RetrievePolicyEntry command) {
        final PolicyId policyId = context.getState();
        if (policy != null) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrievePolicyEntryResponse.of(policyId, optionalEntry.get(), command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            }
        }
        return ResultFactory.newErrorResult(
                policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
    }

    @Override
    public Optional<?> nextETagEntity(final RetrievePolicyEntry command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(p -> p.getEntryFor(command.getLabel()));
    }
}
