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
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.EntryAddition;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntryAdditionResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyImportEntryAddition} command.
 */
final class RetrievePolicyImportEntryAdditionStrategy
        extends AbstractPolicyQueryCommandStrategy<RetrievePolicyImportEntryAddition> {

    RetrievePolicyImportEntryAdditionStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyImportEntryAddition.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyImportEntryAddition command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (policy != null) {
            final Optional<PolicyImport> optionalImport =
                    policy.getPolicyImports().getPolicyImport(importedPolicyId);
            if (optionalImport.isPresent()) {
                final PolicyImport policyImport = optionalImport.get();
                final Optional<EntryAddition> optionalAddition = policyImport.getEffectedImports()
                        .flatMap(ei -> ei.getEntriesAdditions())
                        .flatMap(ea -> ea.getAddition(command.getLabel()));
                if (optionalAddition.isPresent()) {
                    final EntryAddition entryAddition = optionalAddition.get();
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            RetrievePolicyImportEntryAdditionResponse.of(policyId, importedPolicyId,
                                    command.getLabel(),
                                    entryAddition.toJson(dittoHeaders.getSchemaVersion()
                                            .orElse(entryAddition.getLatestSchemaVersion()),
                                            FieldType.regularOrSpecial()),
                                    createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                            policy);
                    return ResultFactory.newQueryResult(command, response);
                }
                return ResultFactory.newErrorResult(
                        policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
            }
        }
        return ResultFactory.newErrorResult(
                policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyImportEntryAddition command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
