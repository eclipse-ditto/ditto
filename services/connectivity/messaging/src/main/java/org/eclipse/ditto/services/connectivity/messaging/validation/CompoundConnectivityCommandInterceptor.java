/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;

/**
 * Accepts multiple {@link ConnectivityCommandInterceptor}s and simply executes all of them.
 */
public final class CompoundConnectivityCommandInterceptor implements ConnectivityCommandInterceptor {

    private final Collection<Consumer<ConnectivityCommand<?>>> validators;

    @SafeVarargs
    public CompoundConnectivityCommandInterceptor(final Consumer<ConnectivityCommand<?>>... validators) {
        this.validators = Arrays.asList(validators);
    }

    @Override
    public void accept(final ConnectivityCommand<?> command) {
        validators.forEach(c -> c.accept(command));
    }
}
