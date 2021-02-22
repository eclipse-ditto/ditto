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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.ditto.model.policies.SubjectAnnouncement;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.ActivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.ActivateTokenIntegrationResponse;
import org.eclipse.ditto.signals.events.policies.PolicyActionEvent;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectModified;
import org.eclipse.ditto.signals.events.policies.SubjectsModifiedPartially;

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
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final SubjectExpiry commandSubjectExpiry = command.getSubjectExpiry();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label)
                .filter(entry -> command.isApplicable(entry, dittoHeaders.getAuthorizationContext()));
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final Set<SubjectId> subjectIds;
            try {
                subjectIds = subjectIdFromActionResolver.resolveSubjectIds(policyEntry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final SubjectType subjectType = PoliciesModelFactory.newSubjectType(
                    MessageFormat.format(MESSAGE_PATTERN_SUBJECT_TYPE, command.getName(), Instant.now().toString()));
            final SubjectExpiry adjustedSubjectExpiry = roundPolicySubjectExpiry(commandSubjectExpiry);
            final SubjectAnnouncement adjustedSubjectAnnouncement =
                    roundSubjectAnnouncement(command.getSubjectAnnouncement().orElse(null));
            final ActivateTokenIntegration adjustedCommand = ActivateTokenIntegration.of(
                    command.getEntityId(), command.getLabel(), subjectIds, adjustedSubjectExpiry.getTimestamp(),
                    dittoHeaders);

            final Set<Subject> adjustedSubjects = subjectIds.stream()
                    .map(subjectId -> Subject.newInstance(subjectId, subjectType, adjustedSubjectExpiry,
                            adjustedSubjectAnnouncement))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            final PolicyBuilder policyBuilder = nonNullPolicy.toBuilder();
            adjustedSubjects.forEach(subject -> policyBuilder.setSubjectFor(label, subject));
            // Validation is necessary because activation may add expiry to the policy admin subject.
            final Policy newPolicy = policyBuilder.build();

            final Optional<Result<PolicyActionEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(newPolicy, dittoHeaders, command);
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
                                dittoHeaders);
                    } else {
                        event = SubjectCreated.of(policyId, label, adjustedSubject, nextRevision, eventTimestamp,
                                dittoHeaders);
                    }
                } else {
                    event = SubjectsModifiedPartially.of(policyId, Map.of(label, adjustedSubjects), nextRevision,
                            eventTimestamp, dittoHeaders);
                }
                final ActivateTokenIntegrationResponse rawResponse =
                        ActivateTokenIntegrationResponse.of(policyId, label, subjectIds, dittoHeaders);
                // do not append ETag - activated subjects do not support ETags.
                return ResultFactory.newMutationResult(adjustedCommand, event, rawResponse);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders),
                        command);
            }
        } else {
            return ResultFactory.newErrorResult(command.getNotApplicableException(dittoHeaders), command);
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
