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
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries}.
 */
final class RetrievePolicyEntriesStrategy extends
        AbstractPolicyQueryCommandStrategy<RetrievePolicyEntries> {

    RetrievePolicyEntriesStrategy() {
        super(RetrievePolicyEntries.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final RetrievePolicyEntries command) {
        final PolicyId policyId = context.getState();
        if (policy != null) {
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    RetrievePolicyEntriesResponse.of(policyId, policy.getEntriesSet(), command.getDittoHeaders()),
                    policy);
            return ResultFactory.newQueryResult(command, response);
        } else {
            return ResultFactory.newErrorResult(policyNotFound(policyId, command.getDittoHeaders()));
        }
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyEntries command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).map(Policy::getEntriesSet).flatMap(EntityTag::fromEntity);
    }
}
