/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportsModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyImports} command.
 */
final class ModifyPolicyImportsStrategy extends AbstractPolicyCommandStrategy<ModifyPolicyImports, PolicyEvent<?>> {

    ModifyPolicyImportsStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyImports.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyImports command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyImports policyImports = command.getPolicyImports();

        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(nonNullPolicy,
                        JsonField.newInstance(Policy.JsonFields.IMPORTS.getPointer(), policyImports.toJson()),
                        command::getDittoHeaders);

        final PolicyId policyId = context.getState();
        final PolicyImportsModified policyImportsModified =
                PolicyImportsModified.of(policyId, policyImports, nextRevision, getEventTimestamp(), dittoHeaders,
                        metadata);
        final WithDittoHeaders response =
                appendETagHeaderIfProvided(command, ModifyPolicyImportsResponse.modified(policyId, dittoHeaders),
                        policy);

        return ResultFactory.newMutationResult(command, policyImportsModified, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImports command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity).map(Policy::getPolicyImports).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImports command, @Nullable final Policy newEntity) {
        return Optional.of(command.getPolicyImports()).flatMap(EntityTag::fromEntity);
    }
}
