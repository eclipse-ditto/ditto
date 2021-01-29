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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegrationResponse;
import org.eclipse.ditto.signals.events.policies.PolicyActionEvent;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.eclipse.ditto.signals.events.policies.SubjectsDeletedPartially;

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

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label)
                .filter(entry -> command.isApplicable(entry, dittoHeaders.getAuthorizationContext()));
        if (optionalEntry.isPresent()) {
            final Set<SubjectId> subjectIds;
            final PolicyEntry policyEntry = optionalEntry.get();
            try {
                subjectIds = subjectIdFromActionResolver.resolveSubjectIds(policyEntry, command);
            } catch (final DittoRuntimeException e) {
                return ResultFactory.newErrorResult(e, command);
            }
            final DeactivateTokenIntegration adjustedCommand =
                    DeactivateTokenIntegration.of(command.getEntityId(), command.getLabel(), subjectIds, dittoHeaders);

            final PolicyActionEvent<?> event;
            final Instant eventTimestamp = getEventTimestamp();
            if (subjectIds.size() == 1) {
                final SubjectId subjectId = subjectIds.stream().findFirst().orElseThrow();
                event = SubjectDeleted.of(policyId, label, subjectId, nextRevision, eventTimestamp, dittoHeaders);
            } else {
                event = SubjectsDeletedPartially.of(policyId, Map.of(label, subjectIds), nextRevision, eventTimestamp,
                        dittoHeaders);
            }
            final DeactivateTokenIntegrationResponse rawResponse =
                    DeactivateTokenIntegrationResponse.of(policyId, label, dittoHeaders);
            return ResultFactory.newMutationResult(adjustedCommand, event, rawResponse);
        } else {
            return ResultFactory.newErrorResult(command.getNotApplicableException(dittoHeaders), command);
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
