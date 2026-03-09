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
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntriesAdditionsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportEntriesAdditionsModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyImportEntriesAdditions} command.
 */
@Immutable
final class ModifyPolicyImportEntriesAdditionsStrategy
        extends AbstractPolicyCommandStrategy<ModifyPolicyImportEntriesAdditions, PolicyEvent<?>> {

    ModifyPolicyImportEntriesAdditionsStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyImportEntriesAdditions.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyImportEntriesAdditions command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final EntriesAdditions entriesAdditions = command.getEntriesAdditions();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyImport> optionalImport =
                nonNullPolicy.getPolicyImports().getPolicyImport(importedPolicyId);
        if (optionalImport.isPresent()) {
            final PolicyImport existingImport = optionalImport.get();
            final PolicyImport newImport = reconstructImportWithEntriesAdditions(existingImport, entriesAdditions);
            nonNullPolicy.toBuilder().setPolicyImport(newImport).build();

            final PolicyImportEntriesAdditionsModified event =
                    PolicyImportEntriesAdditionsModified.of(policyId, importedPolicyId, entriesAdditions,
                            nextRevision, getEventTimestamp(), dittoHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifyPolicyImportEntriesAdditionsResponse.modified(policyId, importedPolicyId,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                    nonNullPolicy);
            return ResultFactory.newMutationResult(command, event, response);
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
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImportEntriesAdditions command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImportEntriesAdditions command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
