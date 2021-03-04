/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyRevision;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyRevisionResponse;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyRevision}
 * command w/o valid authorization context.
 */
final class SudoRetrievePolicyRevisionStrategy extends AbstractPolicyQueryCommandStrategy<SudoRetrievePolicyRevision> {

    SudoRetrievePolicyRevisionStrategy(
            final PolicyConfig policyConfig) {
        super(SudoRetrievePolicyRevision.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy entity,
            final long nextRevision,
            final SudoRetrievePolicyRevision command,
            @Nullable final Metadata metadata) {

        final Optional<Long> revisionOptional = Optional.ofNullable(entity)
                .flatMap(Policy::getRevision)
                .map(PolicyRevision::toLong);

        final WithDittoHeaders<?> response = revisionOptional.<WithDittoHeaders<?>>map(revision ->
                SudoRetrievePolicyRevisionResponse.of(
                        context.getState(), revision, command.getDittoHeaders()))
                .orElseGet(() ->
                        PolicyNotAccessibleException.newBuilder(context.getState())
                                .dittoHeaders(command.getDittoHeaders())
                                .build()
                );

        return ResultFactory.newQueryResult(command, response);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoRetrievePolicyRevision command,
            @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
