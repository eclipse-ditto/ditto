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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEntryDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry} command.
 */
final class DeletePolicyEntryStrategy extends AbstractPolicyCommandStrategy<DeletePolicyEntry> {

    DeletePolicyEntryStrategy() {
        super(DeletePolicyEntry.class);
    }


    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final DeletePolicyEntry command) {
        checkNotNull(policy, "policy");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Label label = command.getLabel();
        final PolicyId policyId = context.getState();

        if (policy.contains(label)) {
            final PoliciesValidator validator = PoliciesValidator.newInstance(policy.removeEntry(label));

            if (validator.isValid()) {
                final PolicyEntryDeleted policyEntryDeleted =
                        PolicyEntryDeleted.of(policyId, label, nextRevision, getEventTimestamp(), dittoHeaders);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        DeletePolicyEntryResponse.of(policyId, label, dittoHeaders), policy);
                return ResultFactory.newMutationResult(command, policyEntryDeleted, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeletePolicyEntry command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(p.getEntryFor(command.getLabel())));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeletePolicyEntry command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
