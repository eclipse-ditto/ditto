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
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResourceResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ResourceCreated;
import org.eclipse.ditto.policies.model.signals.events.ResourceModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource} command.
 */
final class ModifyResourceStrategy extends AbstractPolicyCommandStrategy<ModifyResource, PolicyEvent<?>> {

    ModifyResourceStrategy(final PolicyConfig policyConfig) {
        super(ModifyResource.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyResource command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Resource resource = command.getResource();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(label);
        if (optionalEntry.isPresent()) {

            final JsonPointer resourcePointer = Policy.JsonFields.ENTRIES.getPointer()
                    .append(JsonPointer.of(label))
                    .append(PolicyEntry.JsonFields.RESOURCES.getPointer())
                    .append(JsonPointer.of(resource.getResourceKey()));
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(nonNullPolicy, JsonField.newInstance(resourcePointer, resource.toJson()),
                            () -> dittoHeaders);

            final PoliciesValidator validator =
                    PoliciesValidator.newInstance(nonNullPolicy.setResourceFor(label, resource));

            if (validator.isValid()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                final PolicyEvent<?> eventToPersist;
                final ModifyResourceResponse rawResponse;

                if (policyEntry.getResources().getResource(resource.getResourceKey()).isPresent()) {
                    rawResponse =
                            ModifyResourceResponse.modified(policyId, label, resource.getResourceKey(), dittoHeaders);
                    eventToPersist = ResourceModified.of(policyId, label, resource, nextRevision, getEventTimestamp(),
                            dittoHeaders, metadata);
                } else {
                    rawResponse = ModifyResourceResponse.created(policyId, label, resource, dittoHeaders);
                    eventToPersist = ResourceCreated.of(policyId, label, resource, nextRevision, getEventTimestamp(),
                            dittoHeaders, metadata);
                }

                return ResultFactory.newMutationResult(command, eventToPersist,
                        appendETagHeaderIfProvided(command, rawResponse, nonNullPolicy));
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders), command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyResource command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getResources)
                .flatMap(r -> r.getResource(command.getResource().getResourceKey()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyResource command, @Nullable final Policy newEntity) {
        return Optional.of(command.getResource()).flatMap(EntityTag::fromEntity);
    }
}
