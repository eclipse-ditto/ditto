/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;

/**
 * Authorizes {@link StreamingSubscriptionCommand}s and filters {@link CommandResponse}s.
 */
final class StreamRequestingCommandEnforcement
        extends AbstractEnforcementReloaded<StreamingSubscriptionCommand<?>, CommandResponse<?>>
        implements ThingEnforcementStrategy {

    @Override
    public boolean isApplicable(final Signal<?> signal) {
        return signal instanceof StreamingSubscriptionCommand;
    }

    @Override
    public boolean responseIsApplicable(final CommandResponse<?> commandResponse) {
        return false;
    }

    @Override
    public <S extends Signal<?>, R extends CommandResponse<?>> EnforcementReloaded<S, R> getEnforcement() {
        return (EnforcementReloaded<S, R>) this;
    }

    @Override
    public CompletionStage<StreamingSubscriptionCommand<?>> authorizeSignal(final StreamingSubscriptionCommand<?> signal,
            final PolicyEnforcer policyEnforcer) {

        final ResourceKey resourceKey = ResourceKey.newInstance(signal.getResourceType(), signal.getResourcePath());
        if (policyEnforcer.getEnforcer().hasUnrestrictedPermissions(resourceKey,
                signal.getDittoHeaders().getAuthorizationContext(), Permissions.newInstance(Permission.READ))) {
            return CompletableFuture.completedStage(
                    ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal(signal, policyEnforcer.getEnforcer())
            );
        } else {
            return CompletableFuture.failedStage(
                    ThingNotAccessibleException.newBuilder(ThingId.of(signal.getEntityId()))
                            .dittoHeaders(signal.getDittoHeaders())
                            .build());
        }
    }

    @Override
    public CompletionStage<StreamingSubscriptionCommand<?>> authorizeSignalWithMissingEnforcer(
            final StreamingSubscriptionCommand<?> signal) {

        return CompletableFuture.failedStage(ThingNotAccessibleException.newBuilder(ThingId.of(signal.getEntityId()))
                .dittoHeaders(signal.getDittoHeaders())
                .build());
    }

    @Override
    public boolean shouldFilterCommandResponse(final CommandResponse<?> commandResponse) {
        return false;
    }

    @Override
    public CompletionStage<CommandResponse<?>> filterResponse(final CommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {
        return CompletableFuture.completedStage(commandResponse);
    }
}
