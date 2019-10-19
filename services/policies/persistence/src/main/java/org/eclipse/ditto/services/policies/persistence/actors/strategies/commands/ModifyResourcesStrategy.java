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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyResources} command.
 */
final class ModifyResourcesStrategy extends AbstractPolicyCommandStrategy<ModifyResources> {

    ModifyResourcesStrategy() {
        super(ModifyResources.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final ModifyResources command) {
        checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Resources resources = command.getResources();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        try {
            PolicyCommandSizeValidator.getInstance().ensureValidSize(() -> {
                final List<ResourceKey> rks = resources.stream()
                        .map(Resource::getResourceKey)
                        .collect(Collectors.toList());
                Policy tmpPolicy = policy;
                for (final ResourceKey rk : rks) {
                    tmpPolicy = tmpPolicy.removeResourceFor(label, rk);
                }
                final long policyLength = tmpPolicy.toJsonString().length();
                final long resourcesLength = resources.toJsonString()
                        .length() + 5L;
                return policyLength + resourcesLength;
            }, command::getDittoHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e);
        }

        if (policy.getEntryFor(label).isPresent()) {
            final PoliciesValidator validator =
                    PoliciesValidator.newInstance(policy.setResourcesFor(label, resources));

            if (validator.isValid()) {
                final ResourcesModified event =
                        ResourcesModified.of(policyId, label, resources, nextRevision, getEventTimestamp(),
                                dittoHeaders);
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        ModifyResourcesResponse.of(policyId, label, dittoHeaders), policy);
                return ResultFactory.newMutationResult(command, event, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders));
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyResources command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getResources)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyResources command, @Nullable final Policy newEntity) {
        return Optional.of(command.getResources()).flatMap(EntityTag::fromEntity);
    }
}
