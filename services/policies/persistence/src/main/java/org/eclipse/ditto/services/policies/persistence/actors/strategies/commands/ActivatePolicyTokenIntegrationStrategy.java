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
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegrationResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectsActivated;

/**
 * This strategy handles the {@link ActivatePolicyTokenIntegration} command.
 */
final class ActivatePolicyTokenIntegrationStrategy
        extends AbstractPolicyActionCommandStrategy<ActivatePolicyTokenIntegration> {

    ActivatePolicyTokenIntegrationStrategy(final PolicyConfig policyConfig) {
        super(ActivatePolicyTokenIntegration.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ActivatePolicyTokenIntegration command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final SubjectExpiry commandSubjectExpiry = SubjectExpiry.newInstance(command.getExpiry());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final List<PolicyEntry> entries = command.getLabels()
                .stream()
                .map(nonNullPolicy::getEntryFor)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (entries.isEmpty() || entries.size() != command.getLabels().size()) {
            // Command is constructed incorrectly. This is a bug.
            return ResultFactory.newErrorResult(
                    PolicyActionFailedException.newBuilderForActivateTokenIntegration().build(), command);
        }
        final PolicyBuilder policyBuilder = nonNullPolicy.toBuilder();
        final Map<Label, Subject> activatedSubjects = new HashMap<>();
        for (final PolicyEntry entry : entries) {
            final SubjectId subjectId;
            try {
                subjectId = resolveSubjectId(entry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final Subject subject = Subject.newInstance(subjectId, TOKEN_INTEGRATION, commandSubjectExpiry);
            final Subject adjustedSubject = potentiallyAdjustSubject(subject);
            policyBuilder.setSubjectFor(entry.getLabel(), adjustedSubject);
            activatedSubjects.put(entry.getLabel(), adjustedSubject);
        }

        // Validation is necessary because activation may add expiry to the policy admin subject.
        final Policy newPolicy = policyBuilder.build();
        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final PolicyEvent<?> event =
                    SubjectsActivated.of(policyId, activatedSubjects, nextRevision, getEventTimestamp(), dittoHeaders);
            final ActivatePolicyTokenIntegrationResponse rawResponse =
                    ActivatePolicyTokenIntegrationResponse.of(policyId, command.getSubjectId(), dittoHeaders);
            // do not append ETag - activated subjects do not support ETags.
            return ResultFactory.newMutationResult(command, event, rawResponse);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(policyId, validator.getReason().orElse(null), dittoHeaders),
                    command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ActivatePolicyTokenIntegration command,
            @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ActivatePolicyTokenIntegration command,
            @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
