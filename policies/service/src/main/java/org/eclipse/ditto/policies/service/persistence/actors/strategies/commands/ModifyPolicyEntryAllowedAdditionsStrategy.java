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
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryAllowedAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryAllowedAdditionsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryAllowedAdditionsModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyEntryAllowedAdditions} command.
 */
@Immutable
final class ModifyPolicyEntryAllowedAdditionsStrategy
        extends AbstractPolicyCommandStrategy<ModifyPolicyEntryAllowedAdditions, PolicyEvent<?>> {

    ModifyPolicyEntryAllowedAdditionsStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyEntryAllowedAdditions.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyEntryAllowedAdditions command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Set<AllowedAddition> additions = command.getAllowedAdditions();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        if (nonNullPolicy.getEntryFor(label).isPresent()) {
            final PolicyEntryAllowedAdditionsModified event =
                    PolicyEntryAllowedAdditionsModified.of(policyId, label, additions, nextRevision,
                            getEventTimestamp(), dittoHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifyPolicyEntryAllowedAdditionsResponse.of(policyId, label,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                    nonNullPolicy);
            return ResultFactory.newMutationResult(command, event, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, label, dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyEntryAllowedAdditions command,
            @Nullable final Policy previousEntity) {
        return allowedAdditionsEntityTag(previousEntity, command.getLabel());
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyEntryAllowedAdditions command,
            @Nullable final Policy newEntity) {
        return allowedAdditionsEntityTagForModify(command.getAllowedAdditions());
    }
}
