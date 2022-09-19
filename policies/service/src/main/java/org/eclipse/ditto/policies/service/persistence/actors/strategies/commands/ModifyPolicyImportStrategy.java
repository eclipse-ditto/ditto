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
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.PolicyTooLargeException;
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
        final PolicyId importedPolicyId = policyImport.getImportedPolicyId();

        final JsonObject policyJsonObject = nonNullPolicy.removeEntry(importedPolicyId).toJson();
        final JsonObject policyImportJsonObject = policyImport.toJson();
        final long importedPolicyIdLength = importedPolicyId.toString().length();

        try {
            PolicyCommandSizeValidator.getInstance().ensureValidSize(
                    () -> {
                        final long policyLength = policyJsonObject.getUpperBoundForStringSize();
                        final long entryLengthWithoutLabel = policyImportJsonObject.getUpperBoundForStringSize();
                        final long entryLength = entryLengthWithoutLabel + importedPolicyIdLength + 5L;
                        return policyLength + entryLength;
                    },
                    () -> {
                        final long policyLength = policyJsonObject.toString().length();
                        final long entryLengthWithoutLabel = policyImportJsonObject.toString().length();
                        final long entryLength = entryLengthWithoutLabel + importedPolicyIdLength + 5L;
                        return policyLength + entryLength;
                    },
                    command::getDittoHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e, command);
        }

        final DittoHeadersBuilder<?, ?> adjustedHeadersBuilder = command.getDittoHeaders().toBuilder();
        final DittoHeaders adjustedHeaders = adjustedHeadersBuilder.build();
        final ModifyPolicyImport adjustedCommand = ModifyPolicyImport.of(command.getEntityId(), policyImport,
                adjustedHeaders);

        final PolicyImports newPolicyImports = nonNullPolicy.getImports().map(imports -> imports.setPolicyImport(policyImport)).orElse(PolicyImports.newInstance(policyImport));
        final Policy newPolicy = nonNullPolicy.toBuilder().setPolicyImports(newPolicyImports).build();

        final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                checkForAlreadyExpiredSubject(newPolicy, adjustedHeaders, command);
        if (alreadyExpiredSubject.isPresent()) {
            return alreadyExpiredSubject.get();
        }

        final PolicyId policyId = context.getState();

        final PolicyEvent<?> eventToPersist;
        final ModifyPolicyImportResponse createdOrModifiedResponse;
        if (nonNullPolicy.getImports().map(policyImports -> policyImports.getPolicyImport(importedPolicyId).isPresent()).orElse(false)) {
            eventToPersist =
                    PolicyImportModified.of(policyId,
                            policyImport, nextRevision, getEventTimestamp(),
                            adjustedHeaders, metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.modified(policyId, importedPolicyId, adjustedHeaders);
        } else {
            eventToPersist =
                    PolicyImportCreated.of(policyId, policyImport, nextRevision, getEventTimestamp(),
                            adjustedHeaders, metadata);
            createdOrModifiedResponse = ModifyPolicyImportResponse.created(policyId, policyImport,
                    adjustedHeaders);
        }
        final WithDittoHeaders response =
                appendETagHeaderIfProvided(adjustedCommand, createdOrModifiedResponse, nonNullPolicy);

        return ResultFactory.newMutationResult(adjustedCommand, eventToPersist, response);

    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicyImport command,
            @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> EntityTag.fromEntity(p.getEntryFor(command.getPolicyImport().getImportedPolicyId()).orElse(null)));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicyImport command, @Nullable final Policy newEntity) {
        return Optional.of(command.getPolicyImport()).flatMap(EntityTag::fromEntity);
    }
}
