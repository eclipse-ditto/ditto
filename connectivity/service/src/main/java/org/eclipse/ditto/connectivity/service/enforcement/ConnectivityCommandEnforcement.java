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
package org.eclipse.ditto.connectivity.service.enforcement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;

/**
 * Authorizes {@link ConnectivityCommand}s and filters {@link ConnectivityCommandResponse}s.
 */
public final class ConnectivityCommandEnforcement
        extends AbstractEnforcementReloaded<ConnectivityCommand<?>, ConnectivityCommandResponse<?>> {

    /**
     * Creates a new instance of the connectivity command enforcer.
     */
    public ConnectivityCommandEnforcement() {
    }

    @Override
    public CompletionStage<ConnectivityCommand<?>> authorizeSignal(final ConnectivityCommand<?> command,
            final PolicyEnforcer policyEnforcer) {

        // TODO CR-11344 implement
        return CompletableFuture.completedStage(command);
    }

    @Override
    public CompletionStage<ConnectivityCommand<?>> authorizeSignalWithMissingEnforcer(
            final ConnectivityCommand<?> command) {

        // TODO CR-11344 implement
        return CompletableFuture.completedStage(command);
    }

    @Override
    public boolean shouldFilterCommandResponse(final ConnectivityCommandResponse<?> commandResponse) {
        return false;
    }

    @Override
    public CompletionStage<ConnectivityCommandResponse<?>> filterResponse(
            final ConnectivityCommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        // TODO CR-11344 implement
        return CompletableFuture.completedStage(commandResponse);
    }

}
