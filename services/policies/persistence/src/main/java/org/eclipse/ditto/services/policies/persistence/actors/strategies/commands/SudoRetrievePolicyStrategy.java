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

import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy} command w/o valid authorization context.
 */
final class SudoRetrievePolicyStrategy extends AbstractPolicyQueryCommandStrategy<SudoRetrievePolicy> {

    SudoRetrievePolicyStrategy() {
        super(SudoRetrievePolicy.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
            final long nextRevision, final SudoRetrievePolicy command) {
        final SudoRetrievePolicyResponse rawResponse =
                SudoRetrievePolicyResponse.of(context.getState(), entity, command.getDittoHeaders());
        return ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, rawResponse, entity));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoRetrievePolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
