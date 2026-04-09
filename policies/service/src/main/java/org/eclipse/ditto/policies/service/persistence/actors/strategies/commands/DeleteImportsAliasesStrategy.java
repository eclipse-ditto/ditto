/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteImportsAliasesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasesDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link DeleteImportsAliases} command.
 */
@Immutable
final class DeleteImportsAliasesStrategy
        extends AbstractPolicyCommandStrategy<DeleteImportsAliases, PolicyEvent<?>> {

    DeleteImportsAliasesStrategy(final PolicyConfig policyConfig) {
        super(DeleteImportsAliases.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeleteImportsAliases command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        nonNullPolicy.setImportsAliases(ImportsAliases.emptyInstance());

        final ImportsAliasesDeleted event =
                ImportsAliasesDeleted.of(policyId, nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteImportsAliasesResponse.of(policyId,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                nonNullPolicy);
        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteImportsAliases command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteImportsAliases command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
