/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
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
public class CompoundConnectivityCommandInterceptor implements ConnectivityCommandInterceptor {

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
