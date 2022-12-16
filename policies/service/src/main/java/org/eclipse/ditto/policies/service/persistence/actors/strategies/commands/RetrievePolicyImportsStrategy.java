/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyImports}.
 */
final class RetrievePolicyImportsStrategy extends AbstractPolicyQueryCommandStrategy<RetrievePolicyImports> {

    RetrievePolicyImportsStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyImports.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyImports command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = context.getState();
        if (policy != null) {
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    RetrievePolicyImportsResponse.of(policyId, policy.getPolicyImports(), command.getDittoHeaders()),
                    policy);
            return ResultFactory.newQueryResult(command, response);
        }
        return ResultFactory.newErrorResult(policyImportsNotFound(policyId, command.getDittoHeaders()), command);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyImports command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).map(Policy::getPolicyImports).flatMap(EntityTag::fromEntity);
    }
}
