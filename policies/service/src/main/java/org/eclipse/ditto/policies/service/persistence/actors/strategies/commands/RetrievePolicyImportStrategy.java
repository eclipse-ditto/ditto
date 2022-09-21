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
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link RetrievePolicyImport} command.
 */
final class RetrievePolicyImportStrategy extends AbstractPolicyQueryCommandStrategy<RetrievePolicyImport> {

    RetrievePolicyImportStrategy(final PolicyConfig policyConfig) {
        super(RetrievePolicyImport.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrievePolicyImport command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = context.getState();
        if (policy != null) {
            final Optional<PolicyImport> optionalImport =
                    policy.getPolicyImports().getPolicyImport(command.getImportedPolicyId());
            if (optionalImport.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrievePolicyImportResponse.of(policyId, optionalImport.get(), command.getDittoHeaders()),
                        policy);
                return ResultFactory.newQueryResult(command, response);
            }
        }
        return ResultFactory.newErrorResult(
                policyImportNotFound(policyId, command.getImportedPolicyId(), command.getDittoHeaders()), command);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrievePolicyImport command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity)
                .map(Policy::getPolicyImports)
                .flatMap(im -> EntityTag.fromEntity(im.getPolicyImport(command.getImportedPolicyId()).orElse(null)));
    }
}
