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
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.SubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectAliasConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAliasesResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasesModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifySubjectAliases} command.
 */
@Immutable
final class ModifySubjectAliasesStrategy
        extends AbstractPolicyCommandStrategy<ModifySubjectAliases, PolicyEvent<?>> {

    ModifySubjectAliasesStrategy(final PolicyConfig policyConfig) {
        super(ModifySubjectAliases.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubjectAliases command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final SubjectAliases newSubjectAliases = command.getSubjectAliases();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // Validate: check that no alias label conflicts with existing entry labels
        // and that all targets reference existing imports
        for (final SubjectAlias alias : newSubjectAliases) {
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
                        SubjectAliasConflictException.newBuilder(aliasLabel)
                                .dittoHeaders(dittoHeaders)
                                .build(),
                        command);
            }
        }

        final Policy newPolicy = nonNullPolicy.setSubjectAliases(newSubjectAliases);

        // Validate: all alias targets must reference existing imports
        final Optional<DittoRuntimeException> targetValidationError =
                validateSubjectAliasTargets(newPolicy, dittoHeaders);
        if (targetValidationError.isPresent()) {
            return ResultFactory.newErrorResult(targetValidationError.get(), command);
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final SubjectAliasesModified event =
                    SubjectAliasesModified.of(policyId, newSubjectAliases,
                            nextRevision, getEventTimestamp(), dittoHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifySubjectAliasesResponse.modified(policyId,
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                    nonNullPolicy);
            return ResultFactory.newMutationResult(command, event, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(policyId, validator.getReason().orElse(null), dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifySubjectAliases command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubjectAliases command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
