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
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportEntryAdditionResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportEntryAdditionDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link DeletePolicyImportEntryAddition} command.
 */
final class DeletePolicyImportEntryAdditionStrategy
        extends AbstractPolicyCommandStrategy<DeletePolicyImportEntryAddition, PolicyEvent<?>> {

    DeletePolicyImportEntryAdditionStrategy(final PolicyConfig policyConfig) {
        super(DeletePolicyImportEntryAddition.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeletePolicyImportEntryAddition command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyImport> optionalImport =
                nonNullPolicy.getPolicyImports().getPolicyImport(importedPolicyId);
        if (optionalImport.isPresent()) {
            final PolicyImport existingImport = optionalImport.get();
            final Optional<EntriesAdditions> optionalAdditions = existingImport.getEffectedImports()
                    .flatMap(EffectedImports::getEntriesAdditions);
            if (optionalAdditions.isPresent() && optionalAdditions.get().getAddition(label).isPresent()) {
                final EntriesAdditions newEntriesAdditions = optionalAdditions.get().removeAddition(label);
                final PolicyImport newImport =
                        reconstructImportWithEntriesAdditions(existingImport, newEntriesAdditions);
                nonNullPolicy.toBuilder().setPolicyImport(newImport).build();

                final PolicyImportEntryAdditionDeleted event =
                        PolicyImportEntryAdditionDeleted.of(policyId, importedPolicyId, label,
                                nextRevision, getEventTimestamp(), dittoHeaders, metadata);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        DeletePolicyImportEntryAdditionResponse.of(policyId, importedPolicyId, label,
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                        nonNullPolicy);
                return ResultFactory.newMutationResult(command, event, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
            }
        } else {
            return ResultFactory.newErrorResult(
                    policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
        }
    }

    private static PolicyImport reconstructImportWithEntriesAdditions(final PolicyImport existingImport,
            final EntriesAdditions newEntriesAdditions) {

        final ImportedLabels labels = existingImport.getEffectedImports()
                .map(EffectedImports::getImportedLabels)
                .orElse(PoliciesModelFactory.noImportedEntries());
        final EffectedImports newEffectedImports =
                PoliciesModelFactory.newEffectedImportedLabels(labels, newEntriesAdditions);
        return PoliciesModelFactory.newPolicyImport(existingImport.getImportedPolicyId(), newEffectedImports);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyImportEntryAddition command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyImportEntryAddition command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
