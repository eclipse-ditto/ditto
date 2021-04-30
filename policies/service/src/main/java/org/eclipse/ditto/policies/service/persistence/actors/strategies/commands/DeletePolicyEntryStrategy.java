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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry} command.
 */
final class DeletePolicyEntryStrategy extends AbstractPolicyCommandStrategy<DeletePolicyEntry, PolicyEvent<?>> {

    DeletePolicyEntryStrategy(final PolicyConfig policyConfig) {
        super(DeletePolicyEntry.class, policyConfig);
    }


    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeletePolicyEntry command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Label label = command.getLabel();
        final PolicyId policyId = context.getState();

        if (nonNullPolicy.contains(label)) {
            final PoliciesValidator validator = PoliciesValidator.newInstance(nonNullPolicy.removeEntry(label));

            if (validator.isValid()) {
                final PolicyEntryDeleted policyEntryDeleted =
                        PolicyEntryDeleted.of(policyId, label, nextRevision, getEventTimestamp(), dittoHeaders,
                                metadata);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        DeletePolicyEntryResponse.of(policyId, label, dittoHeaders), nonNullPolicy);
                return ResultFactory.newMutationResult(command, policyEntryDeleted, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders), command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyEntry command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(p.getEntryFor(command.getLabel()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyEntry command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
