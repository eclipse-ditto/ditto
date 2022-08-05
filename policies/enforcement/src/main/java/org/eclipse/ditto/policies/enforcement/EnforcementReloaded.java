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

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Interface providing enforcement/authorization of {@code Signal}s and filtering of {@code CommandResponse}s with the
 * help of a concrete {@link PolicyEnforcer} instance.
 *
 * @param <S> the type of the Signal to enforce/authorize.
 * @param <R> the type of the CommandResponse to filter.
 */
public interface EnforcementReloaded<S extends Signal<?>, R extends CommandResponse<?>> {

    /**
     * Authorizes the passed in {@code signal} using the passed in {@code policyEnforcer}.
     *
     * @param signal the signal to authorize/enforce.
     * @param policyEnforcer the PolicyEnforcer to use for authorizing the signal.
     * @return a CompletionStage with the authorized Signal or a failed stage with a DittoRuntimeException in case of
     * an authorization error.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException for any authorization related errors, e.g.
     * missing access rights. Those have to be caught an interpreted as a command being "unauthorized".
     */
    CompletionStage<S> authorizeSignal(S signal, PolicyEnforcer policyEnforcer);

    /**
     * Authorizes the passed in {@code signal} when no {@code policyEnforcer} is present, e.g. may be used for
     * "creation" commands.
     *
     * @param signal the signal to authorize/enforce.
     * @return a CompletionStage with the authorized Signal or a failed stage with a DittoRuntimeException in case of
     * an authorization error.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException for any authorization related errors, e.g.
     * missing access rights. Those have to be caught an interpreted as a command being "unauthorized".
     */
    CompletionStage<S> authorizeSignalWithMissingEnforcer(S signal);

    /**
     * Checks if for the passed in {@code commandResponse} a filtering should be done at all before trying to filter.
     * Some responses shall e.g. never be filtered - or other implementations may not apply response filtering at all.
     *
     * @param commandResponse the CommandResponse to check if it should be filtered at all.
     * @return {@code true} if the passed in {@code commandResponse} should be filtered.
     */
    boolean shouldFilterCommandResponse(R commandResponse);

    /**
     * Filters the given {@code commandResponse} by using the given {@code policyEnforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param policyEnforcer thePolicyEnforcer that should be used for filtering.
     * @return a CompletionStage with the filtered command response or a failed stage with a DittoRuntimeException.
     */
    CompletionStage<R> filterResponse(R commandResponse, PolicyEnforcer policyEnforcer);

}
