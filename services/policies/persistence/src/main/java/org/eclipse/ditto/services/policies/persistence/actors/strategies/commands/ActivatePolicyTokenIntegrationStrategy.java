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

import java.text.MessageFormat;
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
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.ActivatePolicyTokenIntegrationResponse;
import org.eclipse.ditto.signals.events.policies.PolicyActionEvent;
import org.eclipse.ditto.signals.events.policies.SubjectsModifiedPartially;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link ActivatePolicyTokenIntegration} command.
 */
final class ActivatePolicyTokenIntegrationStrategy
        extends AbstractPolicyActionCommandStrategy<ActivatePolicyTokenIntegration> {

    ActivatePolicyTokenIntegrationStrategy(final PolicyConfig policyConfig, final ActorSystem system) {
        super(ActivatePolicyTokenIntegration.class, policyConfig, system);
    }

    @Override
    protected Result<PolicyActionEvent<?>> doApply(final Context<PolicyId> context,
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
                .filter(this::containsThingReadPermission)
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            return ResultFactory.newErrorResult(getNotApplicableException(), command);
        }
        final PolicyBuilder policyBuilder = nonNullPolicy.toBuilder();
        final Map<Label, Subject> activatedSubjects = new HashMap<>();
        for (final PolicyEntry entry : entries) {
            final SubjectId subjectId;
            try {
                subjectId = subjectIdFromActionResolver.resolveSubjectId(entry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final SubjectType subjectType = PoliciesModelFactory.newSubjectType(
                    MessageFormat.format("added via action <{0}>", command.getName()));
            final Subject subject = Subject.newInstance(subjectId, subjectType, commandSubjectExpiry);
            final Subject adjustedSubject = potentiallyAdjustSubject(subject);
            policyBuilder.setSubjectFor(entry.getLabel(), adjustedSubject);
            activatedSubjects.put(entry.getLabel(), adjustedSubject);
        }

        // Validation is necessary because activation may add expiry to the policy admin subject.
        final Policy newPolicy = policyBuilder.build();
        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final SubjectsModifiedPartially event =
                    SubjectsModifiedPartially.of(policyId, activatedSubjects, nextRevision, getEventTimestamp(),
                            dittoHeaders);
            final ActivatePolicyTokenIntegrationResponse rawResponse =
                    ActivatePolicyTokenIntegrationResponse.of(policyId, dittoHeaders);
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
