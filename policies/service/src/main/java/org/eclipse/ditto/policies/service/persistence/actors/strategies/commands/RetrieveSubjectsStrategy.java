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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects} command.
 */
final class RetrieveSubjectsStrategy extends AbstractPolicyQueryCommandStrategy<RetrieveSubjects> {

    RetrieveSubjectsStrategy(final PolicyConfig policyConfig) {
        super(RetrieveSubjects.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrieveSubjects command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(command.getLabel());
        if (optionalEntry.isPresent()) {
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    RetrieveSubjectsResponse.of(policyId, command.getLabel(), optionalEntry.get().getSubjects(),
                            command.getDittoHeaders()),
                    nonNullPolicy);
            return ResultFactory.newQueryResult(command, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), command.getDittoHeaders()), command);
        }
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveSubjects command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getSubjects)
                .flatMap(EntityTag::fromEntity);
    }
}
