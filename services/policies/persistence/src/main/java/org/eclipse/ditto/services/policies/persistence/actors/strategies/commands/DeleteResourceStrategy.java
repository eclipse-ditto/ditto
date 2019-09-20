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
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.DeleteResource} command.
 */
final class DeleteResourceStrategy extends AbstractPolicyCommandStrategy<DeleteResource> {

    DeleteResourceStrategy() {
        super(DeleteResource.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final DeleteResource command) {
        checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final ResourceKey resourceKey = command.getResourceKey();
        final DittoHeaders headerrs = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();

            if (policyEntry.getResources().getResource(resourceKey).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.removeResourceFor(label, resourceKey));

                if (validator.isValid()) {
                    final ResourceDeleted resourceDeleted =
                            ResourceDeleted.of(policyId, label, resourceKey, nextRevision, getEventTimestamp(),
                                    headerrs);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteResourceResponse.of(policyId, label, resourceKey, headerrs), policy);
                    return ResultFactory.newMutationResult(command, resourceDeleted, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headerrs));
                }
            } else {
                return ResultFactory.newErrorResult(resourceNotFound(policyId, label, resourceKey, headerrs));
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headerrs));
        }
    }

    @Override
    public Optional<?> previousETagEntity(final DeleteResource command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .flatMap(entry -> entry.getResources().getResource(command.getResourceKey()));
    }

    @Override
    public Optional<?> nextETagEntity(final DeleteResource command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
