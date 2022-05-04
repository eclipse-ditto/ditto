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

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * TODO TJ doc
 *
 * @param <S>
 * @param <R>
 */
public interface EnforcementReloaded<S extends Signal<?>, R extends CommandResponse<?>> {

    /**
     *
     * @param signal
     * @param enforcer
     * @return
     */
    CompletionStage<S> authorizeSignal(S signal, PolicyEnforcer enforcer);

    /**
     *
     * @param signal
     * @return
     */
    CompletionStage<S> authorizeSignalWithMissingEnforcer(S signal);

    /**
     * TODO TJ doc
     * @param commandResponse
     * @return
     */
    boolean shouldFilterCommandResponse(R commandResponse);

    /**
     * Filters the given {@code commandResponse} by using the given {@code enforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param enforcer the enforcer that should be used for filtering.
     * @return the filtered command response.
     */
    CompletionStage<R> filterResponse(R commandResponse, PolicyEnforcer enforcer);

    /**
     * TODO TJ doc
     * @param policyEnforcerLoader
     */
    void registerPolicyEnforcerLoader(Function<PolicyId, CompletionStage<PolicyEnforcer>> policyEnforcerLoader);

    /**
     * Allows to register consumers which should be notified if this enforcement implementation received a Policy, e.g.
     * as response of a {@code CreatePolicy} command issued by this implementation.
     * This can optimize the registered consumer as it does not have to load the policy of the policy shard region as
     * a consequence.
     *
     * @param policyInjectionConsumer the consumer to register which shall be notified about an injected Policy.
     */
    void registerPolicyInjectionConsumer(Consumer<Policy> policyInjectionConsumer);

}
