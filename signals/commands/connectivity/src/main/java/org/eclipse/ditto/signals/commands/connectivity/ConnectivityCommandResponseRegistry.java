/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.connectivity;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

/**
 * Registry which is capable of parsing {@link ConnectivityCommandResponse}s from JSON.
 */
@Immutable
public final class ConnectivityCommandResponseRegistry
        extends AbstractCommandResponseRegistry<CommandResponse<?>> {

    private ConnectivityCommandResponseRegistry(
            final Map<String, JsonParsable<CommandResponse<?>>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ConnectivityCommandResponseRegistry}.
     *
     * @return the command response registry.
     */
    public static ConnectivityCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<CommandResponse<?>>> parseStrategies = new HashMap<>();

        parseStrategies.put(TestConnectionResponse.TYPE, TestConnectionResponse::fromJson);
        parseStrategies.put(CreateConnectionResponse.TYPE, CreateConnectionResponse::fromJson);
        parseStrategies.put(ModifyConnectionResponse.TYPE, ModifyConnectionResponse::fromJson);
        parseStrategies.put(DeleteConnectionResponse.TYPE, DeleteConnectionResponse::fromJson);
        parseStrategies.put(OpenConnectionResponse.TYPE, OpenConnectionResponse::fromJson);
        parseStrategies.put(CloseConnectionResponse.TYPE, CloseConnectionResponse::fromJson);

        parseStrategies.put(RetrieveConnectionResponse.TYPE, RetrieveConnectionResponse::fromJson);
        parseStrategies.put(RetrieveConnectionStatusResponse.TYPE, RetrieveConnectionStatusResponse::fromJson);
        parseStrategies.put(RetrieveConnectionMetricsResponse.TYPE, RetrieveConnectionMetricsResponse::fromJson);
        parseStrategies.put(AggregatedConnectivityCommandResponse.TYPE,
                (jsonObject, dittoHeaders) -> AggregatedConnectivityCommandResponse.fromJson(jsonObject, dittoHeaders,
                        parseStrategies));

        return new ConnectivityCommandResponseRegistry(parseStrategies);
    }

}
