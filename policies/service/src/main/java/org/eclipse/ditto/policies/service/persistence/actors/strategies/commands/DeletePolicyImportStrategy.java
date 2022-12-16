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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link DeletePolicyImport} command.
 */
final class DeletePolicyImportStrategy extends AbstractPolicyCommandStrategy<DeletePolicyImport, PolicyEvent<?>> {

    DeletePolicyImportStrategy(final PolicyConfig policyConfig) {
        super(DeletePolicyImport.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeletePolicyImport command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyId importedPolicyId = command.getImportedPolicyId();
        final PolicyId policyId = context.getState();

        if (nonNullPolicy.getPolicyImports().stream().anyMatch(policyImport -> importedPolicyId.equals(policyImport.getImportedPolicyId()))) {
            final PolicyImportDeleted policyImportDeleted =
                    PolicyImportDeleted.of(policyId, importedPolicyId, nextRevision, getEventTimestamp(), dittoHeaders,
                            metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    DeletePolicyImportResponse.of(policyId, importedPolicyId, dittoHeaders), nonNullPolicy);
            return ResultFactory.newMutationResult(command, policyImportDeleted, response);
        } else {
            return ResultFactory.newErrorResult(policyImportNotFound(policyId, importedPolicyId, dittoHeaders),
                    command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyImport command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .map(Policy::getPolicyImports)
                .flatMap(im -> EntityTag.fromEntity(im.getPolicyImport(command.getImportedPolicyId()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyImport command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
