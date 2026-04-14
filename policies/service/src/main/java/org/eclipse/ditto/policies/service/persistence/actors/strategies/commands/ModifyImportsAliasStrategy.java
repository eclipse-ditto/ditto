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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ImportsAliasConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAliasResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasCreated;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyImportsAlias} command.
 */
@Immutable
final class ModifyImportsAliasStrategy
        extends AbstractPolicyCommandStrategy<ModifyImportsAlias, PolicyEvent<?>> {

    ModifyImportsAliasStrategy(final PolicyConfig policyConfig) {
        super(ModifyImportsAlias.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyImportsAlias command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final ImportsAlias importsAlias = command.getImportsAlias();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final JsonPointer aliasPointer = Policy.JsonFields.IMPORTS_ALIASES.getPointer()
                .append(JsonPointer.of(label));
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(nonNullPolicy, JsonField.newInstance(aliasPointer, importsAlias.toJson()),
                        command::getDittoHeaders);

        // Validate: alias must have at least one target
        if (importsAlias.getTargets().isEmpty()) {
            return ResultFactory.newErrorResult(
                    PolicyImportInvalidException.newBuilder()
                            .message("The imports alias '" + label +
                                    "' must have at least one target.")
                            .description("Provide at least one target referencing an import and entry label.")
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }

        // Validate: check that the alias label does not conflict with an existing entry label
        if (nonNullPolicy.getEntryFor(label).isPresent()) {
            return ResultFactory.newErrorResult(
                    ImportsAliasConflictException.newBuilder(label)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }

        // Validate: all alias targets must reference existing imports
        final Policy policyWithNewAlias = nonNullPolicy.setImportsAlias(importsAlias);
        final Optional<DittoRuntimeException> targetValidationError =
                validateImportsAliasTargets(policyWithNewAlias, dittoHeaders);
        if (targetValidationError.isPresent()) {
            return ResultFactory.newErrorResult(targetValidationError.get(), command);
        }

        final boolean aliasExists = nonNullPolicy.getImportsAliases().getAlias(label).isPresent();
        final Policy newPolicy = nonNullPolicy.setImportsAlias(importsAlias);

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final PolicyEvent<?> event;
            final ModifyImportsAliasResponse rawResponse;

            if (aliasExists) {
                event = ImportsAliasModified.of(policyId, label, importsAlias,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
                rawResponse = ModifyImportsAliasResponse.modified(policyId, label,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision));
            } else {
                event = ImportsAliasCreated.of(policyId, label, importsAlias,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
                rawResponse = ModifyImportsAliasResponse.created(policyId, importsAlias,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision));
            }
            return ResultFactory.newMutationResult(command, event,
                    appendETagHeaderIfProvided(command, rawResponse, nonNullPolicy));
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(policyId, validator.getReason().orElse(null), dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyImportsAlias command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyImportsAlias command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
