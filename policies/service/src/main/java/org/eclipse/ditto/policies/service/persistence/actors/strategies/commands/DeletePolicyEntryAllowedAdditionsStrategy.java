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
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryAllowedAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryAllowedAdditionsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryAllowedAdditionsDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * Handles {@link DeletePolicyEntryAllowedAdditions}: clears the entry's allowedAdditions back to
 * absent (the no-restriction tier). PUT [] gives deny-all instead.
 */
@Immutable
final class DeletePolicyEntryAllowedAdditionsStrategy
        extends AbstractPolicyCommandStrategy<DeletePolicyEntryAllowedAdditions, PolicyEvent<?>> {

    DeletePolicyEntryAllowedAdditionsStrategy(final PolicyConfig policyConfig) {
        super(DeletePolicyEntryAllowedAdditions.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeletePolicyEntryAllowedAdditions command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> entryOpt = nonNullPolicy.getEntryFor(label);
        if (entryOpt.isEmpty()) {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, label, dittoHeaders), command);
        }

        // Idempotent DELETE: when the field is already absent, return 204 without emitting an
        // event. This avoids journal pollution and false change-feed notifications when callers
        // retry. The response carries the current revision (no bump) so conditional reads stay
        // coherent.
        if (entryOpt.get().getAllowedAdditions().isEmpty()) {
            final long currentRevision = nextRevision - 1;
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    DeletePolicyEntryAllowedAdditionsResponse.of(policyId, label,
                            createCommandResponseDittoHeaders(dittoHeaders, currentRevision)),
                    nonNullPolicy);
            return ResultFactory.newQueryResult(command, response);
        }

        final PolicyEntryAllowedAdditionsDeleted event =
                PolicyEntryAllowedAdditionsDeleted.of(policyId, label, nextRevision,
                        getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeletePolicyEntryAllowedAdditionsResponse.of(policyId, label,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                nonNullPolicy);
        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyEntryAllowedAdditions command,
            @Nullable final Policy previousEntity) {
        return allowedAdditionsEntityTag(previousEntity, command.getLabel());
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyEntryAllowedAdditions command,
            @Nullable final Policy newEntity) {
        // Post-delete state is always "absent". Use the same hashing path as the other
        // /allowedAdditions strategies so a subsequent GET produces a matching ETag.
        return allowedAdditionsEntityTag(newEntity, command.getLabel());
    }
}
