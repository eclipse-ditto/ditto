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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

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
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.policies.persistence.actors.placeholders.PolicyEntryPlaceholder;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubject;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubjectResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectActivated;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ActivateSubject} command.
 */
final class ActivateSubjectStrategy extends AbstractPolicyCommandStrategy<ActivateSubject> {

    ActivateSubjectStrategy(final PolicyConfig policyConfig) {
        super(ActivateSubject.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ActivateSubject command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final SubjectExpiry commandSubjectExpiry = SubjectExpiry.newInstance(command.getExpiry());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final SubjectId subjectId;
            try {
                subjectId = PolicyEntryPlaceholder.resolveSubjectId(optionalEntry.get(), command.getSubjectId());
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final Subject subject = Subject.newInstance(subjectId, TOKEN_INTEGRATION, commandSubjectExpiry);
            final Subject adjustedSubject = potentiallyAdjustSubject(subject);
            final ActivateSubject adjustedCommand = ActivateSubject.of(
                    command.getEntityId(), command.getLabel(), adjustedSubject.getId(),
                    adjustedSubject.getExpiry().orElseThrow().getTimestamp(), dittoHeaders);

            // Validation is necessary because activation may add expiry to the policy admin subject.
            final Policy newPolicy = nonNullPolicy.setSubjectFor(label, adjustedSubject);
            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
            if (validator.isValid()) {
                final PolicyEvent<?> event =
                        SubjectActivated.of(policyId, label, adjustedSubject, nextRevision, getEventTimestamp(),
                                dittoHeaders);
                final ActivateSubjectResponse rawResponse =
                        ActivateSubjectResponse.of(policyId, label, adjustedSubject.getId(), dittoHeaders);
                // do not append ETag - activated subjects do not support ETags.
                return ResultFactory.newMutationResult(adjustedCommand, event, rawResponse);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders),
                        command);
            }
        } else {
            // Command is constructed incorrectly. This is a bug.
            return ResultFactory.newErrorResult(PolicyActionFailedException.newBuilder().build(), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ActivateSubject command, @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ActivateSubject command, @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
