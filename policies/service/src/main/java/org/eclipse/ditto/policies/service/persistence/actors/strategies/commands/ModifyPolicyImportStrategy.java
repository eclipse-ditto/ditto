/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportReferenceConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyImport} command.
 */
@Immutable
final class ModifyPolicyImportStrategy extends AbstractPolicyCommandStrategy<ModifyPolicyImport, PolicyEvent<?>> {

    ModifyPolicyImportStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyImport.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyImport command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyImport policyImport = command.getPolicyImport();

        final JsonPointer importPointer = Policy.JsonFields.IMPORTS.getPointer()
                .append(JsonPointer.of(policyImport.getImportedPolicyId()));
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(nonNullPolicy, JsonField.newInstance(importPointer, policyImport.toJson()),
                        command::getDittoHeaders);

        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = policyImport.getImportedPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // If this is an update (not a create), reject any reduction of the imported labels filter
        // that would orphan an existing entry reference targeting one of the removed labels.
        // This mirrors the deletion path's `anyEntryReferencesImport` check, but scoped to
        // labels actually being removed by this modification.
        final Optional<PolicyImport> existingImportOpt = nonNullPolicy.getPolicyImports().getPolicyImport(importedPolicyId);
        if (existingImportOpt.isPresent()) {
            final java.util.Set<Label> oldLabels = labelsOf(existingImportOpt.get());
            final java.util.Set<Label> newLabels = labelsOf(policyImport);
            final java.util.Set<Label> removed = new java.util.LinkedHashSet<>(oldLabels);
            removed.removeAll(newLabels);
            if (!removed.isEmpty()) {
                for (final PolicyEntry entry : nonNullPolicy) {
                    for (final EntryReference ref : entry.getReferences()) {
                        if (ref.isImportReference() &&
                                importedPolicyId.equals(ref.getImportedPolicyId().orElse(null)) &&
                                removed.contains(ref.getEntryLabel())) {
                            return ResultFactory.newErrorResult(
                                    PolicyImportReferenceConflictException.newBuilder(policyId, importedPolicyId)
                                            .description("Removing label '" + ref.getEntryLabel() +
                                                    "' from this import's filter would orphan the reference " +
                                                    "from entry '" + entry.getLabel() + "'.")
                                            .dittoHeaders(dittoHeaders)
                                            .build(),
                                    command);
                        }
                    }
                }
            }
        }

        final PolicyEvent<?> eventToPersist;
        final ModifyPolicyImportResponse createdOrModifiedResponse;
        if (existingImportOpt.isPresent()) {
            eventToPersist =
                    PolicyImportModified.of(policyId, policyImport, nextRevision, getEventTimestamp(), dittoHeaders,
                            metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.modified(policyId, importedPolicyId,
                    createCommandResponseDittoHeaders(dittoHeaders, nextRevision)
            );
        } else {
            eventToPersist =
                    PolicyImportCreated.of(policyId, policyImport, nextRevision, getEventTimestamp(), dittoHeaders,
                            metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.created(policyId, policyImport,
                    createCommandResponseDittoHeaders(dittoHeaders, nextRevision)
            );
        }
        final WithDittoHeaders response = appendETagHeaderIfProvided(command, createdOrModifiedResponse, nonNullPolicy);
        return ResultFactory.newMutationResult(command, eventToPersist, response);
    }

    private static java.util.Set<Label> labelsOf(final PolicyImport policyImport) {
        final java.util.Set<Label> labels = new java.util.LinkedHashSet<>();
        policyImport.getEffectedImports()
                .map(EffectedImports::getImportedLabels)
                .ifPresent(labels::addAll);
        return labels;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImport command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(
                        p.getEntryFor(command.getPolicyImport().getImportedPolicyId()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImport command, @Nullable final Policy newEntity) {
        return Optional.of(command.getPolicyImport()).flatMap(EntityTag::fromEntity);
    }
}
