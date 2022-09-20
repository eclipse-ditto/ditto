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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyTooLargeException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * This strategy handles the {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy} command for an
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
        final DittoHeadersBuilder<?, ?> adjustedHeadersBuilder = command.getDittoHeaders().toBuilder();
        final Policy commandPolicy = command.getPolicy();
        final Set<PolicyEntry> adjustedEntries = potentiallyAdjustPolicyEntries(commandPolicy.getEntriesSet());
        final DittoHeaders adjustedHeaders = adjustedHeadersBuilder.build();
        final Policy adjustedPolicy = PoliciesModelFactory.newPolicyBuilder(commandPolicy.getEntityId().orElseThrow(),
                adjustedEntries
        )
                .setPolicyImports(commandPolicy.getPolicyImports().orElse(null))
                .build();

        final ModifyPolicy adjustedCommand = ModifyPolicy.of(command.getEntityId(), adjustedPolicy, adjustedHeaders);

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
                            () -> adjustedHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e, adjustedCommand);
        }

        final Optional<Result<PolicyEvent<?>>> alreadyExpiredSubject =
                checkForAlreadyExpiredSubject(modifiedPolicyWithImplicits, adjustedHeaders, command);
        if (alreadyExpiredSubject.isPresent()) {
            return alreadyExpiredSubject.get();
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(modifiedPolicyWithImplicits);

        if (validator.isValid()) {
            final PolicyModified policyModified =
                    PolicyModified.of(modifiedPolicyWithImplicits, nextRevision, eventTs, adjustedHeaders, metadata);
            final WithDittoHeaders response = appendETagHeaderIfProvided(adjustedCommand,
                    ModifyPolicyResponse.modified(context.getState(), adjustedHeaders),
                    modifiedPolicyWithImplicits);
            return ResultFactory.newMutationResult(adjustedCommand, policyModified, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(context.getState(), validator.getReason().orElse(null), adjustedHeaders), command);
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
