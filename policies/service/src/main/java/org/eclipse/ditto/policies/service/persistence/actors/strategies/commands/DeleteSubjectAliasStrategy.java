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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectAliasNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectAliasResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasDeleted;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link DeleteSubjectAlias} command.
 */
@Immutable
final class DeleteSubjectAliasStrategy
        extends AbstractPolicyCommandStrategy<DeleteSubjectAlias, PolicyEvent<?>> {

    DeleteSubjectAliasStrategy(final PolicyConfig policyConfig) {
        super(DeleteSubjectAlias.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeleteSubjectAlias command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        if (nonNullPolicy.getSubjectAliases().getAlias(label).isEmpty()) {
            return ResultFactory.newErrorResult(
                    SubjectAliasNotAccessibleException.newBuilder(policyId, label)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }

        final SubjectAliasDeleted event =
                SubjectAliasDeleted.of(policyId, label,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteSubjectAliasResponse.of(policyId, label,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                nonNullPolicy);
        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteSubjectAlias command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteSubjectAlias command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
