/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;
import org.eclipse.ditto.policies.model.signals.events.SubjectsDeletedPartially;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link SudoDeleteExpiredSubject} command.
 */
final class SudoDeleteExpiredSubjectStrategy extends
        AbstractPolicyCommandStrategy<SudoDeleteExpiredSubject, PolicyEvent<?>> {

    SudoDeleteExpiredSubjectStrategy(final PolicyConfig policyConfig) {
        super(SudoDeleteExpiredSubject.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final SudoDeleteExpiredSubject command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Subject subject = command.getSubject();
        final DittoHeaders headers = command.getDittoHeaders();

        if (subject.getExpiry().isEmpty()) {
            return ResultFactory.newErrorResult(
                    PolicyNotModifiableException.newBuilder(policyId)
                            .message("Failed to delete expired subject '" + subject.getId() + "'.")
                            .description("The provided subject has no expiry.")
                            .dittoHeaders(headers)
                            .build(),
                    command);
        }

        final Collection<SubjectId> subjectIdCollection = List.of(subject.getId());
        final Map<Label, Collection<SubjectId>> subjectIdsToDelete = nonNullPolicy.stream()
                .filter(entry -> entry.getSubjects()
                        .getSubject(subject.getId())
                        .filter(subject::equals) // only if all fields of the subject are equal, the subject of "other" policyEntries are deleted as well
                        .isPresent())
                .map(PolicyEntry::getLabel)
                .collect(Collectors.toMap(Function.identity(), label -> subjectIdCollection));

        if (subjectIdsToDelete.size() == 1) {
            final var label = subjectIdsToDelete.keySet().iterator().next();
            final var event = SubjectDeleted.of(policyId, label, subject.getId(), nextRevision,
                    getEventTimestamp(), headers, metadata);
            // this command has no response
            return ResultFactory.newMutationResult(command, event, command);
        } else if (!subjectIdsToDelete.isEmpty()) {
            final var event =
                    SubjectsDeletedPartially.of(policyId, subjectIdsToDelete, nextRevision, getEventTimestamp(),
                            headers, metadata);
            // this command has no response
            return ResultFactory.newMutationResult(command, event, command);
        } else {
            // subject already deleted
            return ResultFactory.emptyResult();
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final SudoDeleteExpiredSubject command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoDeleteExpiredSubject command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
