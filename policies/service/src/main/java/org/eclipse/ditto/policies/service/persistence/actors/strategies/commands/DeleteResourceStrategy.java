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
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResourceResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ResourceDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource} command.
 */
final class DeleteResourceStrategy extends AbstractPolicyCommandStrategy<DeleteResource, PolicyEvent<?>> {

    DeleteResourceStrategy(final PolicyConfig policyConfig) {
        super(DeleteResource.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final DeleteResource command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final ResourceKey resourceKey = command.getResourceKey();
        final DittoHeaders headers = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();

            if (policyEntry.getResources().getResource(resourceKey).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(nonNullPolicy.removeResourceFor(label, resourceKey));

                if (validator.isValid()) {
                    final ResourceDeleted resourceDeleted =
                            ResourceDeleted.of(policyId, label, resourceKey, nextRevision, getEventTimestamp(),
                                    headers, metadata);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteResourceResponse.of(policyId, label, resourceKey, headers), nonNullPolicy);
                    return ResultFactory.newMutationResult(command, resourceDeleted, response);
                } else {
                    return ResultFactory.newErrorResult(
                            policyEntryInvalid(policyId, label, validator.getReason().orElse(null), headers), command);
                }
            } else {
                return ResultFactory.newErrorResult(resourceNotFound(policyId, label, resourceKey, headers), command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, headers), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteResource command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .flatMap(entry -> entry.getResources().getResource(command.getResourceKey()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteResource command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
