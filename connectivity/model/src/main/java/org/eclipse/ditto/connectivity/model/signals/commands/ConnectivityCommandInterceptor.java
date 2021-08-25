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
package org.eclipse.ditto.connectivity.model.signals.commands;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;

/**
 * Intercepts a {@link ConnectivityCommand}s and may throw a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}
 * if the command is invalid.
 */
public interface ConnectivityCommandInterceptor extends Consumer<ConnectivityCommand<?>>,
        BiConsumer<ConnectivityCommand<?>, Supplier<Connection>> {

    @Nullable
    default Connection getConnectionFromCommand(final ConnectivityCommand<?> command) {
        switch (command.getType()) {
            case CreateConnection.TYPE:
                return ((CreateConnection) command).getConnection();
            case TestConnection.TYPE:
                return ((TestConnection) command).getConnection();
            case ModifyConnection.TYPE:
                return ((ModifyConnection) command).getConnection();
            default:
                return null;
        }
    }

    /**
     * By default, resolve connection from the given {@link ConnectivityCommand}.
     *
     * @param command the intercepted command
     */
    @Override
    default void accept(final ConnectivityCommand<?> command) {
        accept(command, () -> getConnectionFromCommand(command));
    }

}
