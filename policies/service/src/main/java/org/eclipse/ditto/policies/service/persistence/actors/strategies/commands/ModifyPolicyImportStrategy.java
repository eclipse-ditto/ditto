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
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyPolicyImport} command.
 */
@NotThreadSafe
final class ModifyPolicyImportStrategy extends AbstractPolicyCommandStrategy<ModifyPolicyImport, PolicyEvent<?>> {

    ModifyPolicyImportStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicyImport.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyPolicyImport command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyImport policyImport = command.getPolicyImport();

        final JsonPointer importPointer = Policy.JsonFields.IMPORTS.getPointer()
                .append(JsonPointer.of(policyImport.getImportedPolicyId()));
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(nonNullPolicy, JsonField.newInstance(importPointer, policyImport.toJson()),
                        command::getDittoHeaders);

        final PolicyId policyId = context.getState();
        final PolicyId importedPolicyId = policyImport.getImportedPolicyId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final PolicyEvent<?> eventToPersist;
        final ModifyPolicyImportResponse createdOrModifiedResponse;
        if (nonNullPolicy.getPolicyImports().getPolicyImport(importedPolicyId).isPresent()) {
            eventToPersist =
                    PolicyImportModified.of(policyId, policyImport, nextRevision, getEventTimestamp(), dittoHeaders,
                            metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.modified(policyId, importedPolicyId, dittoHeaders);
        } else {
            eventToPersist =
                    PolicyImportCreated.of(policyId, policyImport, nextRevision, getEventTimestamp(), dittoHeaders,
                            metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.created(policyId, policyImport, dittoHeaders);
        }
        final WithDittoHeaders response = appendETagHeaderIfProvided(command, createdOrModifiedResponse, nonNullPolicy);
        return ResultFactory.newMutationResult(command, eventToPersist, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImport command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(
                        p.getEntryFor(command.getPolicyImport().getImportedPolicyId()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImport command, @Nullable final Policy newEntity) {
        return Optional.of(command.getPolicyImport()).flatMap(EntityTag::fromEntity);
    }
}
