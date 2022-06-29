/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Abstract enforcer of commands performing authorization / enforcement of incoming signals based on policy
 * loaded via the policies shard region.
 *
 * @param <I> the type of the EntityId this enforcer actor enforces commands for.
 * @param <S> the type of the Signals this enforcer actor enforces.
 * @param <R> the type of the CommandResponses this enforcer actor filters.
 * @param <E> the type of the EnforcementReloaded this enforcer actor uses for doing command enforcements.
 */
public abstract class AbstractPolicyLoadingEnforcerActor<I extends EntityId, S extends Signal<?>, R extends CommandResponse<?>,
        E extends EnforcementReloaded<S, R>> extends AbstractEnforcerActor<I, S, R, E> {

    private final PolicyEnforcerProvider policyEnforcerProvider;

    protected AbstractPolicyLoadingEnforcerActor(final I entityId,
            final E enforcement,
            final PolicyEnforcerProvider policyEnforcerProvider) {
        super(entityId, enforcement);
        this.policyEnforcerProvider = policyEnforcerProvider;
    }

    @Override
    protected CompletionStage<Optional<PolicyEnforcer>> providePolicyEnforcer(@Nullable final PolicyId policyId) {
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .exceptionally(error -> Optional.empty());
    }
}
