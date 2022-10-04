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
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry} command.
 */
@NotThreadSafe
final class ModifyPolicyEntryStrategy extends AbstractPolicyCommandStrategy<ModifyPolicyEntry, PolicyEvent<?>> {

    ModifyPolicyEntryStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyEntry.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyEntry command,
            @Nullable final Metadata metadata) {
        
        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders commandHeaders = command.getDittoHeaders();
        final PolicyEntry policyEntry = command.getPolicyEntry();
        final Label label = policyEntry.getLabel();
        final JsonObject policyEntryJsonObject = policyEntry.toJson();

        final JsonPointer entryPointer = Policy.JsonFields.ENTRIES.getPointer().append(JsonPointer.of(label));
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(nonNullPolicy, JsonField.newInstance(entryPointer, policyEntryJsonObject),
                        command::getDittoHeaders);

        final PolicyEntry adjustedPolicyEntry = potentiallyAdjustPolicyEntry(policyEntry);
        final ModifyPolicyEntry adjustedCommand = ModifyPolicyEntry.of(command.getEntityId(), adjustedPolicyEntry,
                commandHeaders);
        final Policy newPolicy = nonNullPolicy.setEntry(adjustedPolicyEntry);

        final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                checkForAlreadyExpiredSubject(newPolicy, commandHeaders, command);
        if (alreadyExpiredSubject.isPresent()) {
            return alreadyExpiredSubject.get();
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        final PolicyId policyId = context.getState();

        if (validator.isValid()) {
            final PolicyEvent<?> eventToPersist;
            final ModifyPolicyEntryResponse createdOrModifiedResponse;
            if (nonNullPolicy.contains(label)) {
                eventToPersist =
                        PolicyEntryModified.of(policyId, adjustedPolicyEntry, nextRevision, getEventTimestamp(),
                                commandHeaders, metadata);
                createdOrModifiedResponse = ModifyPolicyEntryResponse.modified(policyId, label, commandHeaders);
            } else {
                eventToPersist =
                        PolicyEntryCreated.of(policyId, adjustedPolicyEntry, nextRevision, getEventTimestamp(),
                                commandHeaders, metadata);
                createdOrModifiedResponse = ModifyPolicyEntryResponse.created(policyId, adjustedPolicyEntry,
                        commandHeaders);
            }
            final WithDittoHeaders response =
                    appendETagHeaderIfProvided(adjustedCommand, createdOrModifiedResponse, nonNullPolicy);

            return ResultFactory.newMutationResult(adjustedCommand, eventToPersist, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryInvalid(policyId, label, validator.getReason().orElse(null), commandHeaders),
                    command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyEntry command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(p.getEntryFor(command.getPolicyEntry().getLabel()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyEntry command, @Nullable final Policy newEntity) {
        return Optional.of(command.getPolicyEntry()).flatMap(EntityTag::fromEntity);
    }
}
