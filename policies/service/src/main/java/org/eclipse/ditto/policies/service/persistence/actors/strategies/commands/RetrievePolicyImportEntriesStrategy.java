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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntriesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyImportEntries} command.
 */
final class RetrievePolicyImportEntriesStrategy
        extends AbstractPolicyQueryCommandStrategy<RetrievePolicyImportEntries> {

    RetrievePolicyImportEntriesStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyImportEntries.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyImportEntries command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (policy != null) {
            final Optional<PolicyImport> optionalImport =
                    policy.getPolicyImports().getPolicyImport(importedPolicyId);
            if (optionalImport.isPresent()) {
                final PolicyImport policyImport = optionalImport.get();
                final ImportedLabels importedLabels = policyImport.getEffectedImports()
                        .map(ei -> ei.getImportedLabels())
                        .orElse(PoliciesModelFactory.noImportedEntries());
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrievePolicyImportEntriesResponse.of(policyId, importedPolicyId,
                                importedLabels,
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            }
        }
        return ResultFactory.newErrorResult(
                policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyImportEntries command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
