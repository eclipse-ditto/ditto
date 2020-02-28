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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyTooLargeException;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy} command for an already existing Policy.
 */
final class ModifyPolicyStrategy extends AbstractPolicyCommandStrategy<ModifyPolicy> {

    ModifyPolicyStrategy() {
        super(ModifyPolicy.class);
    }

    @Override
    protected Result<PolicyEvent> doApply(final Context<PolicyId> context, @Nullable final Policy entity,
            final long nextRevision, final ModifyPolicy command) {
        final Policy modifiedPolicy = command.getPolicy().toBuilder().setRevision(nextRevision).build();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final JsonObject modifiedPolicyJsonObject = modifiedPolicy.toJson();
        try {
            PolicyCommandSizeValidator.getInstance()
                    .ensureValidSize(
                            modifiedPolicyJsonObject::getUpperBoundForStringSize,
                            () -> modifiedPolicyJsonObject.toString().length(),
                            command::getDittoHeaders);
        } catch (final PolicyTooLargeException e) {
            return ResultFactory.newErrorResult(e);
        }

        final PoliciesValidator validator = PoliciesValidator.newInstance(modifiedPolicy);

        if (validator.isValid()) {
            final PolicyModified policyModified =
                    PolicyModified.of(modifiedPolicy, nextRevision, getEventTimestamp(), dittoHeaders);
            final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                    ModifyPolicyResponse.modified(context.getState(), dittoHeaders),
                    modifiedPolicy);
            return ResultFactory.newMutationResult(command, policyModified, response);
        } else {
            return ResultFactory.newErrorResult(
                    policyInvalid(context.getState(), validator.getReason().orElse(null), dittoHeaders));
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
