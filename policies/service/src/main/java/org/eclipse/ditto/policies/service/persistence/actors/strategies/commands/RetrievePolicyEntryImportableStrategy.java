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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryImportable;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryImportableResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyEntryImportable} command.
 */
final class RetrievePolicyEntryImportableStrategy
        extends AbstractPolicyQueryCommandStrategy<RetrievePolicyEntryImportable> {

    RetrievePolicyEntryImportableStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyEntryImportable.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyEntryImportable command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(command.getLabel());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (optionalEntry.isPresent()) {
            final PolicyEntry entry = optionalEntry.get();
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    RetrievePolicyEntryImportableResponse.of(policyId, command.getLabel(),
                            entry.getImportableType(),
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                    nonNullPolicy);
            return ResultFactory.newQueryResult(command, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyEntryImportable command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
