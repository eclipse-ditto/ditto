/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissionsResponse;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

/**
 * Strategy that handles the {@link CheckPolicyPermissions}.
 * <p>
 * It checks the provided resource permissions against the specified {@link PolicyId} and
 * returns a {@link Map} where the key is the resource and the value is {@code true} if
 * the permission was granted, otherwise {@code false}.
 * @since 3.7.0
 */
final class PolicyCheckPermissionsStrategy extends AbstractPolicyCommandStrategy<CheckPolicyPermissions, PolicyEvent<?>> {

    public PolicyCheckPermissionsStrategy(final PolicyConfig policyConfig) {
        super(CheckPolicyPermissions.class, policyConfig);
    }

    @Override
    protected Result<PolicyEvent<?>> doApply(final Context<PolicyId> context,
            @Nullable final Policy entity,
            final long nextRevision,
            final CheckPolicyPermissions command,
            @Nullable final Metadata metadata) {

        final PolicyId policyId = command.getEntityId();

        if (entity != null) {
            final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(entity);
            final Enforcer enforcer = policyEnforcer.getEnforcer();

            final Map<String, Boolean> permissionResults = new HashMap<>();

            command.getPermissionsMap().forEach((resource, resourcePermission) -> {
                final ResourceKey resourceKey = resourcePermission.getResourceKey();
                boolean hasPermissions = enforcer.hasUnrestrictedPermissions(resourceKey,
                        command.getDittoHeaders().getAuthorizationContext(),
                        Permissions.newInstance(
                                resourcePermission.getPermissions().getFirst(),
                                resourcePermission.getPermissions().stream().skip(1).toArray(String[]::new)));

                permissionResults.put(resource, hasPermissions);
            });

            final WithDittoHeaders response = appendETagHeaderIfProvided(
                    command,
                    CheckPolicyPermissionsResponse.of(policyId, permissionResults, command.getDittoHeaders()),
                    entity
            );

            return ResultFactory.newQueryResult(command, response);

        } else {
            return ResultFactory.newErrorResult(policyNotFound(policyId, command.getDittoHeaders()), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final CheckPolicyPermissions command,
            @Nullable final Policy previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CheckPolicyPermissions command, @Nullable final Policy newEntity) {
        return Optional.empty();
    }
}
