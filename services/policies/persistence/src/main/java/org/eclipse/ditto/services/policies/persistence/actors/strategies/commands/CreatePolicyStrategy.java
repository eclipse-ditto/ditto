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

import java.time.Instant;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy} command for a
 * new Policy.
 */
final class CreatePolicyStrategy extends AbstractPolicyCommandStrategy<CreatePolicy> {

    CreatePolicyStrategy() {
        super(CreatePolicy.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
            final long nextRevision, final CreatePolicy command) {

        // Policy not yet created - do so ..
        final Policy newPolicy = command.getPolicy();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyBuilder newPolicyBuilder = PoliciesModelFactory.newPolicyBuilder(newPolicy);

        if (!newPolicy.getLifecycle().isPresent()) {
            newPolicyBuilder.setLifecycle(PolicyLifecycle.ACTIVE);
        }

        final Policy newPolicyWithLifecycle = newPolicyBuilder.build();
        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicyWithLifecycle);
        if (validator.isValid()) {
            final Instant timestamp = getEventTimestamp();
            final Policy newPolicyWithTimestampAndRevision = newPolicyWithLifecycle.toBuilder()
                    .setModified(timestamp)
                    .setRevision(nextRevision)
                    .build();
            final PolicyCreated policyCreated =
                    PolicyCreated.of(newPolicyWithLifecycle, nextRevision, timestamp, dittoHeaders);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    CreatePolicyResponse.of(context.getState(), newPolicyWithTimestampAndRevision, dittoHeaders),
                    newPolicyWithTimestampAndRevision);
            context.getLog().debug("Created new Policy with ID <{}>.", context.getState());
            return ResultFactory.newMutationResult(command, policyCreated, response, true, false);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(context.getState(), validator.getReason().orElse(null), dittoHeaders));
        }
    }

    @Override
    public Optional<?> previousETagEntity(final CreatePolicy command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final CreatePolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity);
    }

    @Override
    public boolean isDefined(final Context<PolicyId> ctx, @Nullable final Policy policy, final CreatePolicy cmd) {
        return true;
    }
}
