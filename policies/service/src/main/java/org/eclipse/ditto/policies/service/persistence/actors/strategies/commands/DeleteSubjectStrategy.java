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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject} command.
 */
final class DeleteSubjectStrategy extends AbstractPolicyCommandStrategy<DeleteSubject, PolicyEvent<?>> {

    DeleteSubjectStrategy(final PolicyConfig policyConfig) {
        super(DeleteSubject.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeleteSubject command,
            @Nullable final Metadata metadata) {

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
                                    headers, metadata);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteSubjectResponse.of(policyId, label, subjectId, headers), nonNullPolicy);
                    return ResultFactory.newMutationResult(command, subjectDeleted, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headers), command);
                }
            } else {
                return ResultFactory.newErrorResult(subjectNotFound(policyId, label, subjectId, headers), command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headers), command);
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
