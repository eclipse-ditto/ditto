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
package org.eclipse.ditto.signals.commands.connectivity;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ConnectivityCommand}s.
 */
@Immutable
public final class ConnectivityCommandRegistry extends AbstractCommandRegistry<ConnectivityCommand> {

    private ConnectivityCommandRegistry(final Map<String, JsonParsable<ConnectivityCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ConnectivityCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ConnectivityCommandRegistry newInstance() {
        final Map<String, JsonParsable<ConnectivityCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(TestConnection.TYPE, TestConnection::fromJson);
        parseStrategies.put(CreateConnection.TYPE, CreateConnection::fromJson);
        parseStrategies.put(ModifyConnection.TYPE, ModifyConnection::fromJson);
        parseStrategies.put(DeleteConnection.TYPE, DeleteConnection::fromJson);
        parseStrategies.put(OpenConnection.TYPE, OpenConnection::fromJson);
        parseStrategies.put(CloseConnection.TYPE, CloseConnection::fromJson);
        parseStrategies.put(ResetConnectionMetrics.TYPE, ResetConnectionMetrics::fromJson);

        parseStrategies.put(RetrieveConnection.TYPE, RetrieveConnection::fromJson);
        parseStrategies.put(RetrieveConnectionStatus.TYPE, RetrieveConnectionStatus::fromJson);
        parseStrategies.put(RetrieveConnectionMetrics.TYPE, RetrieveConnectionMetrics::fromJson);

        return new ConnectivityCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ConnectivityCommand.TYPE_PREFIX;
    }

}
