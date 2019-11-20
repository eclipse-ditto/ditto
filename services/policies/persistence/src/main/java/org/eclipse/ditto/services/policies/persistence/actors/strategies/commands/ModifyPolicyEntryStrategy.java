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
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry} command.
 */
@NotThreadSafe
final class ModifyPolicyEntryStrategy extends AbstractPolicyCommandStrategy<ModifyPolicyEntry> {

    ModifyPolicyEntryStrategy() {
        super(ModifyPolicyEntry.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy policy,
            final long nextRevision, final ModifyPolicyEntry command) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyEntry policyEntry = command.getPolicyEntry();
        final Label label = policyEntry.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        try {
            PolicyCommandSizeValidator.getInstance().ensureValidSize(() -> {
                final long policyLength = nonNullPolicy.removeEntry(label).toJsonString().length();
                final long entryLength =
                        policyEntry.toJsonString().length() + label.toString().length() + 5L;
                return policyLength + entryLength;
            }, command::getDittoHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e);
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(nonNullPolicy.setEntry(policyEntry));
        final PolicyId policyId = context.getState();

        if (validator.isValid()) {
            final PolicyEvent eventToPersist;
            final ModifyPolicyEntryResponse createdOrModifiedResponse;
            if (nonNullPolicy.contains(label)) {
                eventToPersist =
                        PolicyEntryModified.of(policyId, policyEntry, nextRevision, getEventTimestamp(),
                                dittoHeaders);
                createdOrModifiedResponse = ModifyPolicyEntryResponse.modified(policyId, dittoHeaders);
            } else {
                eventToPersist =
                        PolicyEntryCreated.of(policyId, policyEntry, nextRevision, getEventTimestamp(),
                                dittoHeaders);
                createdOrModifiedResponse = ModifyPolicyEntryResponse.created(policyId, policyEntry, dittoHeaders);
            }
            final WithDittoHeaders response =
                    appendETagHeaderIfProvided(command, createdOrModifiedResponse, nonNullPolicy);

            return ResultFactory.newMutationResult(command, eventToPersist, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryInvalid(policyId, label, validator.getReason().orElse(null), dittoHeaders));
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
