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
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifySubject} command.
 */
final class ModifySubjectStrategy extends AbstractPolicyCommandStrategy<ModifySubject> {

    ModifySubjectStrategy() {
        super(ModifySubject.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final ModifySubject command) {
        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Subject subject = command.getSubject();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final PoliciesValidator validator =
                    PoliciesValidator.newInstance(nonNullPolicy.setSubjectFor(label, subject));

            if (validator.isValid()) {
                final PolicyEvent event;
                final ModifySubjectResponse rawResponse;

                if (policyEntry.getSubjects().getSubject(subject.getId()).isPresent()) {
                    rawResponse = ModifySubjectResponse.modified(policyId, label, dittoHeaders);
                    event = SubjectModified.of(policyId, label, subject, nextRevision, getEventTimestamp(),
                            command.getDittoHeaders());
                } else {
                    rawResponse = ModifySubjectResponse.created(policyId, label, subject, dittoHeaders);
                    event = SubjectCreated.of(policyId, label, subject, nextRevision, getEventTimestamp(),
                            command.getDittoHeaders());
                }
                return ResultFactory.newMutationResult(command, event,
                        appendETagHeaderIfProvided(command, rawResponse, nonNullPolicy));
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifySubject command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .flatMap(entry -> entry.getSubjects().getSubject(command.getSubject().getId()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubject command, @Nullable final Policy newEntity) {
        return Optional.of(command.getSubject()).flatMap(EntityTag::fromEntity);
    }
}
