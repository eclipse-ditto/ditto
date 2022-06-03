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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy} command.
 */
final class RetrievePolicyStrategy extends AbstractPolicyQueryCommandStrategy<RetrievePolicy> {

    RetrievePolicyStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicy.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy entity,
            final long nextRevision,
            final RetrievePolicy command,
            @Nullable final Metadata metadata) {

        if (entity != null) {
            return ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command,
                    RetrievePolicyResponse.of(context.getState(), getPolicyJson(entity, command),
                            command.getDittoHeaders()), entity));
        } else {
            return ResultFactory.newErrorResult(policyNotFound(context.getState(), command.getDittoHeaders()), command);
        }
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    private static JsonObject getPolicyJson(final Policy policy, final PolicyQueryCommand<RetrievePolicy> command) {
        return command.getSelectedFields()
                .map(selectedFields -> policy.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> policy.toJson(command.getImplementedSchemaVersion()));
    }
}
