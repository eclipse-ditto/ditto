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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject} command.
 */
final class DeleteSubjectStrategy extends AbstractPolicyCommandStrategy<DeleteSubject> {

    DeleteSubjectStrategy() {
        super(DeleteSubject.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final DeleteSubject command) {
        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final SubjectId subjectId = command.getSubjectId();
        final DittoHeaders headers = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            if (policyEntry.getSubjects().getSubject(subjectId).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(nonNullPolicy.removeSubjectFor(label, subjectId));

                if (validator.isValid()) {
                    final SubjectDeleted subjectDeleted =
                            SubjectDeleted.of(policyId, label, subjectId, nextRevision, getEventTimestamp(),
                                    headers);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteSubjectResponse.of(policyId, label, subjectId, headers), nonNullPolicy);
                    return ResultFactory.newMutationResult(command, subjectDeleted, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headers));
                }
            } else {
                return ResultFactory.newErrorResult(subjectNotFound(policyId, label, subjectId, headers));
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headers));
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteSubject command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .flatMap(entry -> entry.getSubjects().getSubject(command.getSubjectId()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteSubject command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
