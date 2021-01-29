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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy} command for an
 * already existing Policy.
 */
final class ModifyPolicyStrategy extends AbstractPolicyCommandStrategy<ModifyPolicy, PolicyEvent<?>> {

    ModifyPolicyStrategy(final PolicyConfig policyConfig) {
        super(ModifyPolicy.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy entity,
            final long nextRevision,
            final ModifyPolicy command,
            @Nullable final Metadata metadata) {

        final Instant eventTs = getEventTimestamp();
        final Set<PolicyEntry> adjustedEntries = potentiallyAdjustPolicyEntries(command.getPolicy().getEntriesSet());
        final Policy adjustedPolicy = PoliciesModelFactory.newPolicyBuilder(
                command.getPolicy().getEntityId().orElseThrow(), adjustedEntries).build();

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ModifyPolicy adjustedCommand = ModifyPolicy.of(command.getEntityId(), adjustedPolicy, dittoHeaders);

        final Policy modifiedPolicyWithImplicits = adjustedPolicy.toBuilder()
                .setModified(eventTs)
                .setRevision(nextRevision)
                .build();

        final JsonObject modifiedPolicyJsonObject = modifiedPolicyWithImplicits.toJson();
        try {
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(
                            modifiedPolicyJsonObject::getUpperBoundForStringSize,
                            () -> modifiedPolicyJsonObject.toString().length(),
                            adjustedCommand::getDittoHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e, adjustedCommand);
        }

        final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                checkForAlreadyExpiredSubject(modifiedPolicyWithImplicits, dittoHeaders, command);
        if (alreadyExpiredSubject.isPresent()) {
            return alreadyExpiredSubject.get();
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(modifiedPolicyWithImplicits);

        if (validator.isValid()) {
            final PolicyModified policyModified =
                    PolicyModified.of(modifiedPolicyWithImplicits, nextRevision, eventTs, dittoHeaders);
            final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                    ModifyPolicyResponse.modified(context.getState(), dittoHeaders),
                    modifiedPolicyWithImplicits);
            return ResultFactory.newMutationResult(adjustedCommand, policyModified, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(context.getState(), validator.getReason().orElse(null), dittoHeaders), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyPolicy command, @Nullable final Policy previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyPolicy command, @Nullable final Policy newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
