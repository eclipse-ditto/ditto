/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryAllowedAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryAllowedAdditionsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyEntryAllowedAdditions} command.
 */
final class RetrievePolicyEntryAllowedAdditionsStrategy
        extends AbstractPolicyQueryCommandStrategy<RetrievePolicyEntryAllowedAdditions> {

    RetrievePolicyEntryAllowedAdditionsStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyEntryAllowedAdditions.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyEntryAllowedAdditions command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(command.getLabel());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (optionalEntry.isEmpty()) {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), dittoHeaders), command);
        }
        // The three-tier semantic for allowedAdditions:
        //   absent (Optional.empty)        → no restriction; return 200 + []
        //   present and empty Set          → deny-all; return 200 + []
        //   present and non-empty Set      → return 200 + [those values]
        // /references parallel returns 200 + [] when an entry has no references; matching here
        // avoids confusing "entry not found" (404) with "field unset" (200 + []).
        final Set<AllowedAddition> additions = optionalEntry.get()
                .getAllowedAdditions()
                .orElse(Collections.emptySet());
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                RetrievePolicyEntryAllowedAdditionsResponse.of(policyId, command.getLabel(),
                        additions,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                nonNullPolicy);
        return ResultFactory.newQueryResult(command, response);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyEntryAllowedAdditions command,
            @Nullable final Policy newEntity) {
        return allowedAdditionsEntityTag(newEntity, command.getLabel());
    }
}
