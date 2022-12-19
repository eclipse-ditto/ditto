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

import java.text.MessageFormat;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegrationResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyActionEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link ActivateTokenIntegration} command.
 */
final class ActivateTokenIntegrationStrategy
        extends AbstractPolicyActionCommandStrategy<ActivateTokenIntegration> {

    private static final String MESSAGE_PATTERN_SUBJECT_TYPE = "added via action <{0}> at <{1}>";

    ActivateTokenIntegrationStrategy(final PolicyConfig policyConfig, final ActorSystem system) {
        super(ActivateTokenIntegration.class, policyConfig, system);
    }

    @Override
    protected Result<PolicyActionEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ActivateTokenIntegration command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders commandHeaders = command.getDittoHeaders();
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final SubjectExpiry commandSubjectExpiry = command.getSubjectExpiry();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label)
                .filter(entry -> command.isApplicable(entry, command.getDittoHeaders().getAuthorizationContext()));
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final Set<SubjectId> subjectIds = subjectIdFromActionResolver.resolveSubjectIds(policyEntry, command);
            final SubjectType subjectType = PoliciesModelFactory.newSubjectType(
                    MessageFormat.format(MESSAGE_PATTERN_SUBJECT_TYPE, command.getName(), Instant.now().toString()));
            final SubjectExpiry adjustedSubjectExpiry = roundPolicySubjectExpiry(commandSubjectExpiry);
            final SubjectAnnouncement adjustedSubjectAnnouncement =
                    roundSubjectAnnouncement(command.getSubjectAnnouncement().orElse(null));
            final ActivateTokenIntegration adjustedCommand = ActivateTokenIntegration.of(
                    command.getEntityId(), command.getLabel(), subjectIds, adjustedSubjectExpiry.getTimestamp(),
                    commandHeaders);

            final Set<Subject> adjustedSubjects = subjectIds.stream()
                    .map(subjectId -> Subject.newInstance(subjectId, subjectType, adjustedSubjectExpiry,
                            adjustedSubjectAnnouncement))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            final PolicyBuilder policyBuilder = nonNullPolicy.toBuilder();
            adjustedSubjects.forEach(subject -> policyBuilder.setSubjectFor(label, subject));
            // Validation is necessary because activation may add expiry to the policy admin subject.
            final Policy newPolicy = policyBuilder.build();

            final Optional<Result<PolicyActionEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(newPolicy, commandHeaders, command);
            if (alreadyExpiredSubject.isPresent()) {
                return alreadyExpiredSubject.get();
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
            if (validator.isValid()) {

                final PolicyActionEvent<?> event;
                final Instant eventTimestamp = getEventTimestamp();
                if (adjustedSubjects.size() == 1) {
                    final Subject adjustedSubject = adjustedSubjects.stream().findFirst().orElseThrow();
                    final SubjectId subjectId = adjustedSubject.getId();

                    if (policyEntry.getSubjects().getSubject(subjectId).isPresent()) {
                        event = SubjectModified.of(policyId, label, adjustedSubject, nextRevision, eventTimestamp,
                                commandHeaders, metadata);
                    } else {
                        event = SubjectCreated.of(policyId, label, adjustedSubject, nextRevision, eventTimestamp,
                                commandHeaders, metadata);
                    }
                } else {
                    event = SubjectsModifiedPartially.of(policyId, Map.of(label, adjustedSubjects), nextRevision,
                            eventTimestamp, commandHeaders, metadata);
                }
                final ActivateTokenIntegrationResponse rawResponse =
                        ActivateTokenIntegrationResponse.of(policyId, label, subjectIds, commandHeaders);
                // do not append ETag - activated subjects do not support ETags.
                return ResultFactory.newMutationResult(adjustedCommand, event, rawResponse);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), commandHeaders),
                        command);
            }
        } else {
            return ResultFactory.newErrorResult(command.getNotApplicableException(command.getDittoHeaders()), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ActivateTokenIntegration command,
            @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ActivateTokenIntegration command, @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
