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
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.ImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.ImportsAliasConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAliasesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.ImportsAliasesModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifyImportsAliases} command.
 */
@Immutable
final class ModifyImportsAliasesStrategy
        extends AbstractPolicyCommandStrategy<ModifyImportsAliases, PolicyEvent<?>> {

    ModifyImportsAliasesStrategy(final PolicyConfig policyConfig) {
        super(ModifyImportsAliases.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifyImportsAliases command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final ImportsAliases newImportsAliases = command.getImportsAliases();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // Validate: check that no alias label conflicts with existing entry labels
        // and that all targets reference existing imports
        for (final ImportsAlias alias : newImportsAliases) {
            final Label aliasLabel = alias.getLabel();
            // Validate: alias must have at least one target
            if (alias.getTargets().isEmpty()) {
                return ResultFactory.newErrorResult(
                        PolicyImportInvalidException.newBuilder()
                                .message("The subject alias '" + aliasLabel +
                                        "' must have at least one target.")
                                .description(
                                        "Provide at least one target referencing an import and entry label.")
                                .dittoHeaders(dittoHeaders)
                                .build(),
                        command);
            }
            if (nonNullPolicy.getEntryFor(aliasLabel).isPresent()) {
                return ResultFactory.newErrorResult(
                        ImportsAliasConflictException.newBuilder(aliasLabel)
                                .dittoHeaders(dittoHeaders)
                                .build(),
                        command);
            }
        }

        final Policy newPolicy = nonNullPolicy.setImportsAliases(newImportsAliases);

        // Validate: all alias targets must reference existing imports
        final Optional<DittoRuntimeException> targetValidationError =
                validateImportsAliasTargets(newPolicy, dittoHeaders);
        if (targetValidationError.isPresent()) {
            return ResultFactory.newErrorResult(targetValidationError.get(), command);
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final ImportsAliasesModified event =
                    ImportsAliasesModified.of(policyId, newImportsAliases,
                            nextRevision, getEventTimestamp(), dittoHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifyImportsAliasesResponse.modified(policyId,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                    nonNullPolicy);
            return ResultFactory.newMutationResult(command, event, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(policyId, validator.getReason().orElse(null), dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyImportsAliases command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyImportsAliases command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
