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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegrationResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyActionEvent;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link DeactivateTokenIntegration} command.
 */
final class DeactivateTokenIntegrationStrategy
        extends AbstractPolicyActionCommandStrategy<DeactivateTokenIntegration> {

    DeactivateTokenIntegrationStrategy(final PolicyConfig policyConfig, final ActorSystem system) {
        super(DeactivateTokenIntegration.class, policyConfig, system);
    }

    @Override
    protected Result<PolicyActionEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeactivateTokenIntegration command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final SubjectId subjectId;
            final PolicyEntry policyEntry = optionalEntry.get();
            try {
                subjectId = subjectIdFromActionResolver.resolveSubjectId(policyEntry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final DeactivateTokenIntegration adjustedCommand =
                    DeactivateTokenIntegration.of(command.getEntityId(), command.getLabel(), subjectId, dittoHeaders);

            final Optional<Subject> subject = policyEntry.getSubjects().getSubject(subjectId);
            if (subject.filter(s -> s.getExpiry().isEmpty()).isPresent()) {
                // It is forbidden to deactivate a permanent subject.
                final DittoRuntimeException error =
                        PolicyActionFailedException.newBuilderForDeactivatingPermanentSubjects()
                                .dittoHeaders(dittoHeaders)
                                .build();
                return ResultFactory.newErrorResult(error, command);
            } else {
                // Expiring subjects are not considered for validation. The result is always valid.
                final SubjectDeleted event =
                        SubjectDeleted.of(policyId, label, subjectId, nextRevision, getEventTimestamp(),
                                dittoHeaders);
                final DeactivateTokenIntegrationResponse rawResponse =
                        DeactivateTokenIntegrationResponse.of(policyId, label, subjectId, dittoHeaders);
                return ResultFactory.newMutationResult(adjustedCommand, event, rawResponse);
            }
        } else {
            // Policy is configured incorrectly
            return ResultFactory.newErrorResult(
                    PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build(),
                    command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeactivateTokenIntegration command,
            @Nullable final Policy previousEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeactivateTokenIntegration command,
            @Nullable final Policy newEntity) {
        // activated subjects do not support entity tag
        return Optional.empty();
    }
}
