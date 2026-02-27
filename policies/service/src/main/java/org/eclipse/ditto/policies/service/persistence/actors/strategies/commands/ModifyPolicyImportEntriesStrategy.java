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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntriesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportEntriesModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyImportEntries} command.
 */
@Immutable
final class ModifyPolicyImportEntriesStrategy
        extends AbstractPolicyCommandStrategy<ModifyPolicyImportEntries, PolicyEvent<?>> {

    ModifyPolicyImportEntriesStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyImportEntries.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyImportEntries command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final ImportedLabels importedLabels = command.getImportedLabels();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyImport> optionalImport =
                nonNullPolicy.getPolicyImports().getPolicyImport(importedPolicyId);
        if (optionalImport.isPresent()) {
            final PolicyImport existingImport = optionalImport.get();
            final PolicyImport newImport = reconstructImportWithEntries(existingImport, importedLabels);
            nonNullPolicy.toBuilder().setPolicyImport(newImport).build();

            final PolicyImportEntriesModified event =
                    PolicyImportEntriesModified.of(policyId, importedPolicyId, importedLabels,
                            nextRevision, getEventTimestamp(), dittoHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifyPolicyImportEntriesResponse.modified(policyId, importedPolicyId,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                    nonNullPolicy);
            return ResultFactory.newMutationResult(command, event, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
        }
    }

    private static PolicyImport reconstructImportWithEntries(final PolicyImport existingImport,
            final ImportedLabels newLabels) {

        final EntriesAdditions entriesAdditions = existingImport.getEffectedImports()
                .flatMap(EffectedImports::getEntriesAdditions)
                .orElse(PoliciesModelFactory.emptyEntriesAdditions());
        final EffectedImports newEffectedImports =
                PoliciesModelFactory.newEffectedImportedLabels(newLabels, entriesAdditions);
        return PoliciesModelFactory.newPolicyImport(existingImport.getImportedPolicyId(), newEffectedImports);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImportEntries command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImportEntries command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
