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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects} command.
 */
final class ModifySubjectsStrategy extends AbstractPolicyCommandStrategy<ModifySubjects, PolicyEvent<?>> {

    ModifySubjectsStrategy(final PolicyConfig policyConfig) {
        super(ModifySubjects.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy policy,
            final long nextRevision,
            final ModifySubjects command,
            @Nullable final Metadata metadata) {

        final Policy nonNullPolicy = checkNotNull(policy, "policy");
        final PolicyId policyId = context.getState();
        final Label label = command.getLabel();
        final Subjects subjects = command.getSubjects();
        final DittoHeaders commandHeaders = command.getDittoHeaders();

        if (nonNullPolicy.getEntryFor(label).isPresent()) {

            final JsonPointer resourcesPointer = Policy.JsonFields.ENTRIES.getPointer()
                    .append(JsonPointer.of(label))
                    .append(PolicyEntry.JsonFields.SUBJECTS.getPointer());
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(nonNullPolicy, JsonField.newInstance(resourcesPointer, subjects.toJson()),
                            () -> commandHeaders);

            final Subjects adjustedSubjects = potentiallyAdjustSubjects(subjects);
            final ModifySubjects adjustedCommand = ModifySubjects.of(command.getEntityId(), command.getLabel(),
                    adjustedSubjects, commandHeaders);

            final Policy newPolicy = nonNullPolicy.setSubjectsFor(label, adjustedSubjects);

            final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                    checkForAlreadyExpiredSubject(newPolicy, commandHeaders, command);
            if (alreadyExpiredSubject.isPresent()) {
                return alreadyExpiredSubject.get();
            }

            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicy);

            if (validator.isValid()) {
                final SubjectsModified subjectsModified =
                        SubjectsModified.of(policyId, label, adjustedSubjects, nextRevision, getEventTimestamp(),
                                commandHeaders, metadata);
                final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                        ModifySubjectsResponse.of(policyId, label, commandHeaders), nonNullPolicy);
                return ResultFactory.newMutationResult(adjustedCommand, subjectsModified, response);
            } else {
                return ResultFactory.newErrorResult(
                        policyEntryInvalid(policyId, label, validator.getReason().orElse(null), commandHeaders),
                        command);
            }
        } else {
            return ResultFactory.newErrorResult(policyEntryNotFound(policyId, label, command.getDittoHeaders()), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifySubjects command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(p -> p.getEntryFor(command.getLabel()))
                .map(PolicyEntry::getSubjects)
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifySubjects command, @Nullable final Policy newEntity) {
        return Optional.of(command.getSubjects()).flatMap(EntityTag::fromEntity);
    }
}
