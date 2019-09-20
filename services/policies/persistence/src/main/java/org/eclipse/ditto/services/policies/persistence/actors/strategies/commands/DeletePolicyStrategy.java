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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy} command.
 */
final class DeletePolicyStrategy extends AbstractPolicyCommandStrategy<DeletePolicy> {

    DeletePolicyStrategy() {
        super(DeletePolicy.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
            final long nextRevision, final DeletePolicy command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyDeleted policyDeleted =
                PolicyDeleted.of(context.getState(), nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeletePolicyResponse.of(context.getState(), dittoHeaders), entity);
        context.getLog().info("Deleted Policy with ID <{}>.", context.getState());
        return ResultFactory.newMutationResult(command, policyDeleted, response, false, true);
    }

    @Override
    public Optional<?> previousETagEntity(final DeletePolicy command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final DeletePolicy command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
