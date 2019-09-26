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

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy} command for an
 * already existing Policy.
 */
final class PolicyConflictStrategy extends AbstractPolicyCommandStrategy<CreatePolicy> {

    PolicyConflictStrategy() {
        super(CreatePolicy.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
            final long nextRevision, final CreatePolicy command) {
        return ResultFactory.newErrorResult(PolicyConflictException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

    @Override
    public Optional<?> previousETagEntity(final CreatePolicy command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final CreatePolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity);
    }
}
