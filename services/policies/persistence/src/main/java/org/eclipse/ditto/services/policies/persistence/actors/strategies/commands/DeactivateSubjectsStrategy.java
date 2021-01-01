/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyActionFailedException;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.policies.persistence.actors.placeholders.PolicyEntryPlaceholder;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.DeactivateSubjects;
import org.eclipse.ditto.signals.commands.policies.modify.DeactivateSubjectsResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectsDeactivated;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeactivateSubjects} command.
 */
final class DeactivateSubjectsStrategy extends AbstractPolicyActionCommandStrategy<DeactivateSubjects> {

    DeactivateSubjectsStrategy(final PolicyConfig policyConfig) {
        super(DeactivateSubjects.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeactivateSubjects command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final List<PolicyEntry> entries = command.getLabels()
                .stream()
                .map(nonNullPolicy::getEntryFor)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (entries.isEmpty() || entries.size() != command.getLabels().size()) {
            // Command is constructed incorrectly. This is a bug.
            return ResultFactory.newErrorResult(
                    PolicyActionFailedException.newBuilderForDeactivateTokenIntegration().build(), command);
        }
        final Map<Label, SubjectId> deactivatedSubjectsIds = new HashMap<>();
        for (final PolicyEntry entry : entries) {
            final SubjectId subjectId;
            try {
                subjectId = resolveSubjectId(entry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final Optional<Subject> optionalSubject = entry.getSubjects().getSubject(subjectId);
            if (optionalSubject.isPresent()) {
                if (optionalSubject.get().getExpiry().isEmpty()) {
                    return ResultFactory.newErrorResult(
                            PolicyActionFailedException.newBuilderForDeactivatingPermanentSubjects()
                                    .dittoHeaders(dittoHeaders)
                                    .build(),
                            command);
                }
                deactivatedSubjectsIds.put(entry.getLabel(), subjectId);
            }
        }
        // Validation is not necessary because temporary subjects do not affect validity
        final PolicyEvent<?> event =
                SubjectsDeactivated.of(policyId, deactivatedSubjectsIds, nextRevision, getEventTimestamp(),
                        dittoHeaders);
        final DeactivateSubjectsResponse rawResponse =
                DeactivateSubjectsResponse.of(policyId, command.getSubjectId(), dittoHeaders);
        return ResultFactory.newMutationResult(command, event, rawResponse);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeactivateSubjects command,
            @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeactivateSubjects command, @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
