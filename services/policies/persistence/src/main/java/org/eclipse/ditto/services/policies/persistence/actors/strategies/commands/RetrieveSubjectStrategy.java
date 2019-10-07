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

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject} command.
 */
final class RetrieveSubjectStrategy extends AbstractPolicyQueryCommandStrategy<RetrieveSubject> {

    RetrieveSubjectStrategy() {
        super(RetrieveSubject.class);
    }


    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final RetrieveSubject command) {
        checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final Optional<Subject> optionalSubject = policyEntry.getSubjects().getSubject(command.getSubjectId());
            if (optionalSubject.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrieveSubjectResponse.of(policyId, command.getLabel(), optionalSubject.get(),
                                command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            } else {
                return ResultFactory.newErrorResult(
                        subjectNotFound(policyId, command.getLabel(), command.getSubjectId(),
                                command.getDittoHeaders()));
            }
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()));
        }
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveSubject command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getSubjects)
                .flatMap(s -> s.getSubject(command.getSubjectId()))
                .flatMap(EntityTag::fromEntity);
    }
}
