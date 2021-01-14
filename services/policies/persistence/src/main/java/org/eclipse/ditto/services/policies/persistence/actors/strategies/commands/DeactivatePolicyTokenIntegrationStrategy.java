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
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivatePolicyTokenIntegrationResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.signals.events.policies.PolicyActionEvent;
import org.eclipse.ditto.signals.events.policies.SubjectsDeletedPartially;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link DeactivatePolicyTokenIntegration} command.
 */
final class DeactivatePolicyTokenIntegrationStrategy
        extends AbstractPolicyActionCommandStrategy<DeactivatePolicyTokenIntegration> {

    DeactivatePolicyTokenIntegrationStrategy(final PolicyConfig policyConfig, final ActorSystem system) {
        super(DeactivatePolicyTokenIntegration.class, policyConfig, system);
    }

    @Override
    protected Result<PolicyActionEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeactivatePolicyTokenIntegration command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final List<PolicyEntry> preFilteredEntries = command.getLabels()
                .stream()
                .map(nonNullPolicy::getEntryFor)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (preFilteredEntries.isEmpty() || preFilteredEntries.size() != command.getLabels().size()) {
            // Command is constructed incorrectly. This is a bug.
            return ResultFactory.newErrorResult(
                    PolicyActionFailedException.newBuilderForDeactivateTokenIntegration().build(), command);
        }

        final List<PolicyEntry> entries = preFilteredEntries.stream()
                .filter(entry -> containsAuthenticatedSubject(entry, dittoHeaders.getAuthorizationContext()))
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            return ResultFactory.newErrorResult(getNotApplicableException(dittoHeaders), command);
        }

        final Map<Label, SubjectId> deactivatedSubjectsIds = new HashMap<>();
        for (final PolicyEntry entry : entries) {
            final SubjectId subjectId;
            try {
                subjectId = subjectIdFromActionResolver.resolveSubjectId(entry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final Optional<Subject> optionalSubject = entry.getSubjects().getSubject(subjectId);
            if (optionalSubject.isPresent()) {
                deactivatedSubjectsIds.put(entry.getLabel(), subjectId);
            }
        }
        // Validation is not necessary because temporary subjects do not affect validity
        final SubjectsDeletedPartially event =
                SubjectsDeletedPartially.of(policyId, deactivatedSubjectsIds, nextRevision, getEventTimestamp(),
                        dittoHeaders);
        final DeactivatePolicyTokenIntegrationResponse rawResponse =
                DeactivatePolicyTokenIntegrationResponse.of(policyId, dittoHeaders);
        return ResultFactory.newMutationResult(command, event, rawResponse);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeactivatePolicyTokenIntegration command,
            @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeactivatePolicyTokenIntegration command,
            @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
