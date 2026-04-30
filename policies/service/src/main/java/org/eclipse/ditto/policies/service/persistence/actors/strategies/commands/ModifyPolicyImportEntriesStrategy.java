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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.Label;
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
        if (optionalImport.isEmpty()) {
            return ResultFactory.newErrorResult(
                    policyImportNotFound(policyId, importedPolicyId, dittoHeaders), command);
        }
        final PolicyImport existingImport = optionalImport.get();
        final Set<Label> oldLabels = labelsOf(existingImport);
        final Set<Label> newLabels = new LinkedHashSet<>(importedLabels);
        final Optional<DittoRuntimeException> orphanError =
                checkLabelNarrowingDoesNotOrphan(policyId, nonNullPolicy, importedPolicyId,
                        oldLabels, newLabels, dittoHeaders);
        if (orphanError.isPresent()) {
            return ResultFactory.newErrorResult(orphanError.get(), command);
        }

        final PolicyImport newImport = reconstructImportWithEntries(existingImport, importedLabels);
        final Policy newPolicy = nonNullPolicy.toBuilder().setPolicyImport(newImport).build();

        final PolicyImportEntriesModified event =
                PolicyImportEntriesModified.of(policyId, importedPolicyId, importedLabels,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyPolicyImportEntriesResponse.modified(policyId, importedPolicyId,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                newPolicy);
        return ResultFactory.newMutationResult(command, event, response);
    }

    private static PolicyImport reconstructImportWithEntries(final PolicyImport existingImport,
            final ImportedLabels newLabels) {

        // Preserve the existing transitiveImports — only the imported-labels filter is being modified.
        return PoliciesModelFactory.newPolicyImport(existingImport.getImportedPolicyId(),
                PoliciesModelFactory.newEffectedImportedLabels(newLabels, existingImport.getTransitiveImports()));
    }

    private static Set<Label> labelsOf(final PolicyImport policyImport) {
        final Set<Label> labels = new LinkedHashSet<>();
        policyImport.getEffectedImports()
                .map(EffectedImports::getImportedLabels)
                .ifPresent(labels::addAll);
        return labels;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImportEntries command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getPolicyImports().getPolicyImport(command.getImportedPolicyId()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImportEntries command,
            @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity)
                .flatMap(p -> p.getPolicyImports().getPolicyImport(command.getImportedPolicyId()))
                .flatMap(EntityTag::fromEntity);
    }
}
