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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
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
final class ModifySubjectStrategy extends AbstractPolicyCommandStrategy<ModifySubject, PolicyEvent<?>> {

    ModifySubjectStrategy(final PolicyConfig policyConfig) {
        super(ModifySubject.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubject command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Subject subject = command.getSubject();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final DittoHeadersBuilder<?, ?> adjustedHeadersBuilder = command.getDittoHeaders().toBuilder();
            final Subject adjustedSubject = potentiallyAdjustSubject(subject);
            final DittoHeaders adjustedHeaders = adjustedHeadersBuilder.build();
            final ModifySubject adjustedCommand = ModifySubject.of(
                    command.getEntityId(), command.getLabel(), adjustedSubject, adjustedHeaders);

            final Policy newPolicy = nonNullPolicy.setSubjectFor(label, adjustedSubject);

            final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(newPolicy, adjustedHeaders, command);
            if (alreadyExpiredSubject.isPresent()) {
                return alreadyExpiredSubject.get();
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);

            if (validator.isValid()) {
                final PolicyEvent<?> event;
                final ModifySubjectResponse rawResponse;

                if (policyEntry.getSubjects().getSubject(adjustedSubject.getId()).isPresent()) {
                    rawResponse = ModifySubjectResponse.modified(policyId, label, adjustedSubject.getId(),
                            adjustedHeaders);
                    event = SubjectModified.of(policyId, label, adjustedSubject, nextRevision, getEventTimestamp(),
                            adjustedHeaders);
                } else {
                    rawResponse = ModifySubjectResponse.created(policyId, label, adjustedSubject, adjustedHeaders);
                    event = SubjectCreated.of(policyId, label, adjustedSubject, nextRevision, getEventTimestamp(),
                            adjustedHeaders);
                }
                return ResultFactory.newMutationResult(adjustedCommand, event,
                        appendETagHeaderIfProvided(adjustedCommand, rawResponse, nonNullPolicy));
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), adjustedHeaders),
                        command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, command.getDittoHeaders()),
                    command);
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
