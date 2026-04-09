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
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportsNotModifiableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportsDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link DeletePolicyImports} command.
 */
@Immutable
final class DeletePolicyImportsStrategy
        extends AbstractPolicyCommandStrategy<DeletePolicyImports, PolicyEvent<?>> {

    DeletePolicyImportsStrategy(final PolicyConfig policyConfig) {
        super(DeletePolicyImports.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeletePolicyImports command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyId policyId = context.getState();

        // Check if any subject alias references any import
        for (final SubjectAlias alias : nonNullPolicy.getSubjectAliases()) {
            if (!alias.getTargets().isEmpty()) {
                return ResultFactory.newErrorResult(
                        PolicyImportsNotModifiableException.newBuilder(policyId)
                                .message("The imports of the Policy with ID '" + policyId +
                                        "' cannot be deleted because they are referenced by subject alias '" +
                                        alias.getLabel() + "'.")
                                .description(
                                        "Remove all subject aliases first before deleting the imports.")
                                .dittoHeaders(dittoHeaders)
                                .build(),
                        command);
            }
        }

        final Policy newPolicy = nonNullPolicy.toBuilder()
                .setPolicyImports(PolicyImports.emptyInstance())
                .build();

        final PolicyImportsDeleted event =
                PolicyImportsDeleted.of(policyId, nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeletePolicyImportsResponse.of(policyId,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                nonNullPolicy);
        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyImports command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .map(Policy::getPolicyImports)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyImports command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
