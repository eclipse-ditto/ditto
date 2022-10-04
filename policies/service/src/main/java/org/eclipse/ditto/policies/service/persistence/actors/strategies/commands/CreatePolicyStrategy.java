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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy} command for a
 * new Policy.
 */
final class CreatePolicyStrategy extends AbstractPolicyCommandStrategy<CreatePolicy, PolicyEvent<?>> {

    CreatePolicyStrategy(final PolicyConfig policyConfig) {
        super(CreatePolicy.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy entity,
            final long nextRevision,
            final CreatePolicy command,
            @Nullable final Metadata metadata) {

        // Policy not yet created - do so ..
        final Policy newPolicy = command.getPolicy();
        final DittoHeaders commandHeaders = command.getDittoHeaders();
        final Set<PolicyEntry> adjustedEntries = potentiallyAdjustPolicyEntries(command.getPolicy().getEntriesSet());
        final PolicyBuilder newPolicyBuilder = newPolicy.toBuilder().setId(newPolicy.getEntityId().orElseThrow())
                .setAll(adjustedEntries);

        final Policy adjustedPolicy = newPolicyBuilder.build();
        final CreatePolicy adjustedCommand = CreatePolicy.of(adjustedPolicy, commandHeaders);

        if (newPolicy.getLifecycle().isEmpty()) {
            newPolicyBuilder.setLifecycle(PolicyLifecycle.ACTIVE);
        }
        final Policy newPolicyWithLifecycle = newPolicyBuilder.build();

        final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                checkForAlreadyExpiredSubject(newPolicyWithLifecycle, commandHeaders, command);
        if (alreadyExpiredSubject.isPresent()) {
            return alreadyExpiredSubject.get();
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicyWithLifecycle);
        if (validator.isValid()) {
            final Instant timestamp = getEventTimestamp();
            final Policy newPolicyWithImplicits = newPolicyWithLifecycle.toBuilder()
                    .setModified(timestamp)
                    .setCreated(timestamp)
                    .setRevision(nextRevision)
                    .setMetadata(metadata)
                    .build();
            final PolicyCreated policyCreated =
                    PolicyCreated.of(newPolicyWithImplicits, nextRevision, timestamp, commandHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                    CreatePolicyResponse.of(context.getState(), newPolicyWithImplicits, commandHeaders),
                    newPolicyWithImplicits);
            context.getLog().withCorrelationId(adjustedCommand)
                    .debug("Created new Policy with ID <{}>.", context.getState());
            return ResultFactory.newMutationResult(adjustedCommand, policyCreated, response, true, false);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(context.getState(), validator.getReason().orElse(null), commandHeaders),
                    command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final CreatePolicy command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CreatePolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public boolean isDefined(final Context<PolicyId> ctx, @Nullable final Policy policy, final CreatePolicy cmd) {
        return true;
    }
}
