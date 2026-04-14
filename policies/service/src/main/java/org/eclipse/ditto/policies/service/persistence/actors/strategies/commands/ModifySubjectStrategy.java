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

import java.util.List;
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
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.ImportsAliasTarget;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasSubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasSubjectModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject} command.
 */
@Immutable
final class ModifySubjectStrategy extends AbstractPolicyCommandStrategy<ModifySubject, PolicyEvent<?>> {

    ModifySubjectStrategy(final PolicyConfig policyConfig) {
        super(ModifySubject.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubject command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Subject subject = command.getSubject();
        final DittoHeaders commandHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {

            final JsonPointer resourcesPointer = Policy.JsonFields.ENTRIES.getPointer()
                    .append(JsonPointer.of(label))
                    .append(PolicyEntry.JsonFields.SUBJECTS.getPointer())
                    .append(JsonPointer.of(subject.getId()));
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(nonNullPolicy, JsonField.newInstance(resourcesPointer, subject.toJson()),
                            () -> commandHeaders);

            final PolicyEntry policyEntry = optionalEntry.get();
            final Subject adjustedSubject = potentiallyAdjustSubject(subject);
            final ModifySubject adjustedCommand = ModifySubject.of(
                    command.getEntityId(), command.getLabel(), adjustedSubject, commandHeaders);

            final Policy newPolicy = nonNullPolicy.setSubjectFor(label, adjustedSubject);

            final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(adjustedSubject, commandHeaders, command);
            if (alreadyExpiredSubject.isPresent()) {
                return alreadyExpiredSubject.get();
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);

            if (validator.isValid()) {
                final PolicyEvent<?> event;
                final ModifySubjectResponse rawResponse;

                if (policyEntry.getSubjects().getSubject(adjustedSubject.getId()).isPresent()) {
                    rawResponse = ModifySubjectResponse.modified(policyId, label, adjustedSubject.getId(),
                            createCommandResponseDittoHeaders(commandHeaders, nextRevision)
                    );
                    event = SubjectModified.of(policyId, label, adjustedSubject, nextRevision, getEventTimestamp(),
                            commandHeaders, metadata);
                } else {
                    rawResponse = ModifySubjectResponse.created(policyId, label, adjustedSubject,
                            createCommandResponseDittoHeaders(commandHeaders, nextRevision)
                    );
                    event = SubjectCreated.of(policyId, label, adjustedSubject, nextRevision, getEventTimestamp(),
                            commandHeaders, metadata);
                }
                return ResultFactory.newMutationResult(adjustedCommand, event,
                        appendETagHeaderIfProvided(adjustedCommand, rawResponse, nonNullPolicy));
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), commandHeaders),
                        command);
            }
        } else {
            // Check if label is an imports alias
            final Optional<ImportsAlias> aliasOpt = nonNullPolicy.getImportsAliases().getAlias(label);
            if (aliasOpt.isPresent()) {
                return handleAliasSubject(nonNullPolicy, policyId, label, subject, commandHeaders, command,
                        aliasOpt.get(), nextRevision, metadata);
            }
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, command.getDittoHeaders()),
                    command);
        }
    }

    private Result<PolicyEvent<?>> handleAliasSubject(final Policy policy, final PolicyId policyId,
            final Label aliasLabel, final Subject subject, final DittoHeaders commandHeaders,
            final ModifySubject command, final ImportsAlias alias, final long nextRevision,
            @Nullable final Metadata metadata) {

        final Subject adjustedSubject = potentiallyAdjustSubject(subject);
        final Optional<Result<PolicyEvent<?>>> alreadyExpired =
                checkForAlreadyExpiredSubject(adjustedSubject, commandHeaders, command);
        if (alreadyExpired.isPresent()) {
            return alreadyExpired.get();
        }

        final List<ImportsAliasTarget> targets = alias.getTargets();

        // Check if subject existed in ANY target to determine Created vs Modified
        final boolean subjectExisted = targets.stream()
                .anyMatch(target -> subjectExistsInTarget(policy, target, adjustedSubject));

        // Fan out single subject to all alias targets' entriesAdditions
        Policy updatedPolicy = policy;
        for (final ImportsAliasTarget target : targets) {
            updatedPolicy = applySubjectToTarget(updatedPolicy, target, adjustedSubject);
        }

        // Size validation after fan-out
        final var updatedPolicyJson = updatedPolicy.toJson();
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(updatedPolicyJson::getUpperBoundForStringSize,
                        () -> updatedPolicyJson.toString().length(), () -> commandHeaders);

        final ModifySubject adjustedCommand = ModifySubject.of(policyId, aliasLabel, adjustedSubject, commandHeaders);
        final PolicyEvent<?> event;
        final ModifySubjectResponse rawResponse;

        if (subjectExisted) {
            event = ImportsAliasSubjectModified.of(policyId, aliasLabel, adjustedSubject, targets, nextRevision,
                    getEventTimestamp(), commandHeaders, metadata);
            rawResponse = ModifySubjectResponse.modified(policyId, aliasLabel, adjustedSubject.getId(),
                    createCommandResponseDittoHeaders(commandHeaders, nextRevision));
        } else {
            event = ImportsAliasSubjectCreated.of(policyId, aliasLabel, adjustedSubject, targets, nextRevision,
                    getEventTimestamp(), commandHeaders, metadata);
            rawResponse = ModifySubjectResponse.created(policyId, aliasLabel, adjustedSubject,
                    createCommandResponseDittoHeaders(commandHeaders, nextRevision));
        }
        return ResultFactory.newMutationResult(adjustedCommand, event,
                appendETagHeaderIfProvided(adjustedCommand, rawResponse, policy));
    }

    private static boolean subjectExistsInTarget(final Policy policy, final ImportsAliasTarget target,
            final Subject subject) {

        return policy.getPolicyImports()
                .getPolicyImport(target.getImportedPolicyId())
                .flatMap(PolicyImport::getEntriesAdditions)
                .flatMap(additions -> additions.getAddition(target.getEntryLabel()))
                .flatMap(addition -> addition.getSubjects())
                .flatMap(subjects -> subjects.getSubject(subject.getId()))
                .isPresent();
    }

    private static Policy applySubjectToTarget(final Policy policy, final ImportsAliasTarget target,
            final Subject subject) {

        final PolicyImports imports = policy.getPolicyImports();
        return imports.getPolicyImport(target.getImportedPolicyId())
                .map(existingImport -> {
                    final EntriesAdditions existingAdditions = existingImport.getEntriesAdditions()
                            .orElse(PoliciesModelFactory.emptyEntriesAdditions());
                    final var existingAddition = existingAdditions.getAddition(target.getEntryLabel()).orElse(null);
                    final Subjects existingSubjects = existingAddition != null ?
                            existingAddition.getSubjects().orElse(PoliciesModelFactory.emptySubjects()) :
                            PoliciesModelFactory.emptySubjects();
                    final Subjects newSubjects = existingSubjects.setSubject(subject);
                    final var newAddition = PoliciesModelFactory.newEntryAddition(
                            target.getEntryLabel(), newSubjects,
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
    public Optional<EntityTag> previousEntityTag(final ModifySubject command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .flatMap(entry -> entry.getSubjects().getSubject(command.getSubject().getId()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubject command, @Nullable final Policy newEntity) {
        return Optional.of(command.getSubject()).flatMap(EntityTag::fromEntity);
    }
}
