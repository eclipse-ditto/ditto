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
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.ImportsAliasTarget;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject} command.
 */
final class RetrieveSubjectStrategy extends AbstractPolicyQueryCommandStrategy<RetrieveSubject> {

    RetrieveSubjectStrategy(final PolicyConfig policyConfig) {
        super(RetrieveSubject.class, policyConfig);
    }


    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final RetrieveSubject command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Optional<PolicyEntry> optionalEntry = nonNullPolicy.getEntryFor(command.getLabel());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (optionalEntry.isPresent()) {
            final PolicyEntry policyEntry = optionalEntry.get();
            final Optional<Subject> optionalSubject = policyEntry.getSubjects().getSubject(command.getSubjectId());
            if (optionalSubject.isPresent()) {
                final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                        RetrieveSubjectResponse.of(policyId, command.getLabel(), optionalSubject.get(),
                                createCommandResponseDittoHeaders(dittoHeaders, nextRevision-1)
                        ),
                        nonNullPolicy);
                return ResultFactory.newQueryResult(command, response);
            } else {
                return ResultFactory.newErrorResult(
                        subjectNotFound(policyId, command.getLabel(), command.getSubjectId(),
                                dittoHeaders), command);
            }
        } else {
            // Check if label is an imports alias
            final Optional<ImportsAlias> aliasOpt = nonNullPolicy.getImportsAliases().getAlias(command.getLabel());
            if (aliasOpt.isPresent()) {
                final ImportsAlias alias = aliasOpt.get();
                final Optional<Subject> aliasSubject = resolveFirstTargetSubject(nonNullPolicy, alias,
                        command.getSubjectId());
                if (aliasSubject.isPresent()) {
                    final WithDittoHeaders aliasResponse = appendETagHeaderIfProvided(command,
                            RetrieveSubjectResponse.of(policyId, command.getLabel(), aliasSubject.get(),
                                    createCommandResponseDittoHeaders(dittoHeaders, nextRevision - 1)),
                            nonNullPolicy);
                    return ResultFactory.newQueryResult(command, aliasResponse);
                } else {
                    return ResultFactory.newErrorResult(
                            subjectNotFound(policyId, command.getLabel(), command.getSubjectId(), dittoHeaders),
                            command);
                }
            }
            return ResultFactory.newErrorResult(
                    policyEntryNotFound(policyId, command.getLabel(), dittoHeaders), command);
        }
    }

    private static Optional<Subject> resolveFirstTargetSubject(final Policy policy, final ImportsAlias alias,
            final SubjectId subjectId) {

        if (alias.getTargets().isEmpty()) {
            return Optional.empty();
        }
        final ImportsAliasTarget firstTarget = alias.getTargets().get(0);
        return policy.getPolicyImports()
                .getPolicyImport(firstTarget.getImportedPolicyId())
                .flatMap(PolicyImport::getEntriesAdditions)
                .flatMap(additions -> additions.getAddition(firstTarget.getEntryLabel()))
                .flatMap(addition -> addition.getSubjects())
                .flatMap(subjects -> subjects.getSubject(subjectId));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveSubject command, @Nullable final Policy newEntity) {
        if (newEntity == null) {
            return Optional.empty();
        }
        final Optional<Subject> regularSubject = newEntity.getEntryFor(command.getLabel())
                .map(PolicyEntry::getSubjects)
                .flatMap(s -> s.getSubject(command.getSubjectId()));
        if (regularSubject.isPresent()) {
            return EntityTag.fromEntity(regularSubject.get());
        }
        // Fall back to alias-resolved subject
        return newEntity.getImportsAliases().getAlias(command.getLabel())
                .flatMap(alias -> resolveFirstTargetSubject(newEntity, alias, command.getSubjectId()))
                .flatMap(EntityTag::fromEntity);
    }
}
