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
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.SubjectAliasConflictException;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAliasResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectAliasModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link ModifySubjectAlias} command.
 */
@Immutable
final class ModifySubjectAliasStrategy
        extends AbstractPolicyCommandStrategy<ModifySubjectAlias, PolicyEvent<?>> {

    ModifySubjectAliasStrategy(final PolicyConfig policyConfig) {
        super(ModifySubjectAlias.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubjectAlias command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final SubjectAlias subjectAlias = command.getSubjectAlias();
        final Label label = command.getLabel();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // Validate: alias must have at least one target
        if (subjectAlias.getTargets().isEmpty()) {
            return ResultFactory.newErrorResult(
                    PolicyImportInvalidException.newBuilder()
                            .message("The subject alias '" + label +
                                    "' must have at least one target.")
                            .description("Provide at least one target referencing an import and entry label.")
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }

        // Validate: check that the alias label does not conflict with an existing entry label
        if (nonNullPolicy.getEntryFor(label).isPresent()) {
            return ResultFactory.newErrorResult(
                    SubjectAliasConflictException.newBuilder(label)
                            .dittoHeaders(dittoHeaders)
                            .build(),
                    command);
        }

        // Validate: all alias targets must reference existing imports
        final Policy policyWithNewAlias = nonNullPolicy.setSubjectAlias(subjectAlias);
        final Optional<DittoRuntimeException> targetValidationError =
                validateSubjectAliasTargets(policyWithNewAlias, dittoHeaders);
        if (targetValidationError.isPresent()) {
            return ResultFactory.newErrorResult(targetValidationError.get(), command);
        }

        final boolean aliasExists = nonNullPolicy.getSubjectAliases().getAlias(label).isPresent();
        final Policy newPolicy = nonNullPolicy.setSubjectAlias(subjectAlias);

        final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);
        if (validator.isValid()) {
            final PolicyEvent<?> event;
            final ModifySubjectAliasResponse rawResponse;

            if (aliasExists) {
                event = SubjectAliasModified.of(policyId, label, subjectAlias,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
                rawResponse = ModifySubjectAliasResponse.modified(policyId, label,
                        createCommandResponseDittoHeaders(dittoHeaders, nextRevision));
            } else {
                event = SubjectAliasCreated.of(policyId, label, subjectAlias,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
                rawResponse = ModifySubjectAliasResponse.created(policyId, subjectAlias,
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
    public Optional<EntityTag> previousEntityTag(final ModifySubjectAlias command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubjectAlias command,
            @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
