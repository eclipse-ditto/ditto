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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.ImportsAliasTarget;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects} command.
 */
final class RetrieveSubjectsStrategy extends AbstractPolicyQueryCommandStrategy<RetrieveSubjects> {

    RetrieveSubjectsStrategy(final PolicyConfig policyConfig) {
        super(RetrieveSubjects.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrieveSubjects command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(command.getLabel());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (optionalEntry.isPresent()) {
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    RetrieveSubjectsResponse.of(policyId, command.getLabel(), optionalEntry.get().getSubjects(),
                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision-1)),
                    nonNullPolicy);
            return ResultFactory.newQueryResult(command, response);
        } else {
            // Check if label is a subject alias
            final Optional<ImportsAlias> aliasOpt = nonNullPolicy.getImportsAliases().getAlias(command.getLabel());
            if (aliasOpt.isPresent()) {
                final ImportsAlias alias = aliasOpt.get();
                final Subjects subjects = resolveFirstTargetSubjects(nonNullPolicy, alias);
                final WithDittoHeaders aliasResponse = appendETagHeaderIfProvided(command,
                        RetrieveSubjectsResponse.of(policyId, command.getLabel(), subjects,
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                        nonNullPolicy);
                return ResultFactory.newQueryResult(command, aliasResponse);
            }
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), dittoHeaders), command);
        }
    }

    private static Subjects resolveFirstTargetSubjects(final Policy policy, final ImportsAlias alias) {
        if (alias.getTargets().isEmpty()) {
            return PoliciesModelFactory.emptySubjects();
        }
        final ImportsAliasTarget firstTarget = alias.getTargets().get(0);
        return policy.getPolicyImports()
                .getPolicyImport(firstTarget.getImportedPolicyId())
                .flatMap(PolicyImport::getEntriesAdditions)
                .flatMap(additions -> additions.getAddition(firstTarget.getEntryLabel()))
                .flatMap(addition -> addition.getSubjects())
                .orElse(PoliciesModelFactory.emptySubjects());
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveSubjects command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getSubjects)
                .flatMap(EntityTag::fromEntity);
    }
}
