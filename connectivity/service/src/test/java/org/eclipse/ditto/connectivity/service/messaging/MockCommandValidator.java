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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnavailableException;
import org.eclipse.ditto.connectivity.service.messaging.validation.CustomConnectivityCommandInterceptorProvider;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

public class MockCommandValidator implements CustomConnectivityCommandInterceptorProvider {

    /**
     * @param actorSystem the actor system in which to load the extension.
     * @param config the config the extension is configured.
     */
    protected MockCommandValidator(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public ConnectivityCommandInterceptor getCommandInterceptor() {
        return new ExceptionalValidator();
    }

    public static class ExceptionalValidator implements ConnectivityCommandInterceptor {

        @Override
        public void accept(final ConnectivityCommand<?> connectivityCommand,
                final Supplier<Connection> connectionSupplier) {

            final Boolean shouldThrowException =
                    Optional.ofNullable(connectivityCommand.getDittoHeaders().get("validator-should-throw-exception"))
                            .map(Boolean::parseBoolean)
                            .orElse(false);
            if (shouldThrowException) {
                throw ConnectionUnavailableException.newBuilder(connectionSupplier.get().getId())
                        .dittoHeaders(connectivityCommand.getDittoHeaders())
                        .message("not valid")
                        .build();
            }
        }
    }
}
