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
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.SubjectAliasTarget;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasSubjectsModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects} command.
 */
@Immutable
final class ModifySubjectsStrategy extends AbstractPolicyCommandStrategy<ModifySubjects, PolicyEvent<?>> {

    ModifySubjectsStrategy(final PolicyConfig policyConfig) {
        super(ModifySubjects.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubjects command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Subjects subjects = command.getSubjects();
        final DittoHeaders commandHeaders = command.getDittoHeaders();

        if (nonNullPolicy.getEntryFor(label).isPresent()) {

            final JsonPointer resourcesPointer = Policy.JsonFields.ENTRIES.getPointer()
                    .append(JsonPointer.of(label))
                    .append(PolicyEntry.JsonFields.SUBJECTS.getPointer());
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(nonNullPolicy, JsonField.newInstance(resourcesPointer, subjects.toJson()),
                            () -> commandHeaders);

            final Subjects adjustedSubjects = potentiallyAdjustSubjects(subjects);
            final ModifySubjects adjustedCommand = ModifySubjects.of(command.getEntityId(), command.getLabel(),
                    adjustedSubjects, commandHeaders);

            final Policy newPolicy = nonNullPolicy.setSubjectsFor(label, adjustedSubjects);

            final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(adjustedSubjects, commandHeaders, command);
            if (alreadyExpiredSubject.isPresent()) {
                return alreadyExpiredSubject.get();
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);

            if (validator.isValid()) {
                final SubjectsModified subjectsModified =
                        SubjectsModified.of(policyId, label, adjustedSubjects, nextRevision, getEventTimestamp(),
                                commandHeaders, metadata);
                final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                        ModifySubjectsResponse.of(policyId, label,
                                createCommandResponseDittoHeaders(commandHeaders, nextRevision)
                        ), nonNullPolicy);
                return ResultFactory.newMutationResult(adjustedCommand, subjectsModified, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), commandHeaders),
                        command);
            }
        } else {
            // Check if label is a subject alias
            final Optional<SubjectAlias> aliasOpt = nonNullPolicy.getSubjectAliases().getAlias(label);
            if (aliasOpt.isPresent()) {
                return handleAliasSubjects(nonNullPolicy, policyId, label, subjects, commandHeaders, command,
                        aliasOpt.get(), nextRevision, metadata);
            }
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, command.getDittoHeaders()), command);
        }
    }

    private Result<PolicyEvent<?>> handleAliasSubjects(final Policy policy, final PolicyId policyId,
            final Label aliasLabel, final Subjects subjects, final DittoHeaders commandHeaders,
            final ModifySubjects command, final SubjectAlias alias, final long nextRevision,
            @Nullable final Metadata metadata) {

        final Subjects adjustedSubjects = potentiallyAdjustSubjects(subjects);
        final Optional<Result<PolicyEvent<?>>> alreadyExpired =
                checkForAlreadyExpiredSubject(adjustedSubjects, commandHeaders, command);
        if (alreadyExpired.isPresent()) {
            return alreadyExpired.get();
        }

        // Fan out subjects to all alias targets' entriesAdditions
        Policy updatedPolicy = policy;
        for (final SubjectAliasTarget target : alias.getTargets()) {
            updatedPolicy = applySubjectsToTarget(updatedPolicy, target, adjustedSubjects);
        }

        // Size validation after fan-out (subjects are written N times for N targets)
        final var updatedPolicyJson = updatedPolicy.toJson();
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(updatedPolicyJson::getUpperBoundForStringSize,
                        () -> updatedPolicyJson.toString().length(), () -> commandHeaders);

        final SubjectAliasSubjectsModified event = SubjectAliasSubjectsModified.of(policyId, aliasLabel,
                adjustedSubjects, alias.getTargets(), nextRevision, getEventTimestamp(), commandHeaders, metadata);
        final ModifySubjects adjustedCommand = ModifySubjects.of(policyId, aliasLabel, adjustedSubjects,
                commandHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                ModifySubjectsResponse.of(policyId, aliasLabel,
                        createCommandResponseDittoHeaders(commandHeaders, nextRevision)),
                policy);
        return ResultFactory.newMutationResult(adjustedCommand, event, response);
    }

    private static Policy applySubjectsToTarget(final Policy policy, final SubjectAliasTarget target,
            final Subjects subjects) {

        final PolicyImports imports = policy.getPolicyImports();
        return imports.getPolicyImport(target.getImportedPolicyId())
                .map(existingImport -> {
                    final EntriesAdditions existingAdditions = existingImport.getEntriesAdditions()
                            .orElse(PoliciesModelFactory.emptyEntriesAdditions());
                    final var existingAddition = existingAdditions.getAddition(target.getEntryLabel()).orElse(null);
                    final var newAddition = PoliciesModelFactory.newEntryAddition(
                            target.getEntryLabel(), subjects,
                            existingAddition != null ? existingAddition.getResources().orElse(null) : null,
                            existingAddition != null ? existingAddition.getNamespaces().orElse(null) : null);
                    final EntriesAdditions newAdditions = existingAdditions.setAddition(newAddition);
                    final var labels = existingImport.getEffectedImports()
                            .map(ei -> ei.getImportedLabels())
                            .orElse(PoliciesModelFactory.noImportedEntries());
                    final var newImport = PoliciesModelFactory.newPolicyImport(
                            existingImport.getImportedPolicyId(),
                            PoliciesModelFactory.newEffectedImportedLabels(labels, newAdditions));
                    final PolicyImports newImports = imports.setPolicyImport(newImport);
                    return policy.toBuilder().setPolicyImports(newImports).build();
                })
                .orElse(policy);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifySubjects command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getSubjects)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubjects command, @Nullable final Policy newEntity) {
        return Optional.of(command.getSubjects()).flatMap(EntityTag::fromEntity);
    }
}
