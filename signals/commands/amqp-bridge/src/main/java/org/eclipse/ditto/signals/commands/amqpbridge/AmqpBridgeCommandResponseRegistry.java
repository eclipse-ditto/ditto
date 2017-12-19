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
package org.eclipse.ditto.signals.commands.amqpbridge;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;

import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusesResponse;

/**
 * Registry which is capable of parsing {@link AmqpBridgeCommandResponse}s from JSON.
 */
@Immutable
public final class AmqpBridgeCommandResponseRegistry
        extends AbstractCommandResponseRegistry<AmqpBridgeCommandResponse> {

    private AmqpBridgeCommandResponseRegistry(
            final Map<String, JsonParsable<AmqpBridgeCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code AmqpBridgeCommandResponseRegistry}.
     *
     * @return the command response registry.
     */
    public static AmqpBridgeCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<AmqpBridgeCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreateConnectionResponse.TYPE, CreateConnectionResponse::fromJson);
        parseStrategies.put(DeleteConnectionResponse.TYPE, DeleteConnectionResponse::fromJson);
        parseStrategies.put(OpenConnectionResponse.TYPE, OpenConnectionResponse::fromJson);
        parseStrategies.put(CloseConnectionResponse.TYPE, CloseConnectionResponse::fromJson);

        parseStrategies.put(RetrieveConnectionResponse.TYPE, RetrieveConnectionResponse::fromJson);
        parseStrategies.put(RetrieveConnectionStatusResponse.TYPE, RetrieveConnectionStatusResponse::fromJson);
        parseStrategies.put(RetrieveConnectionStatusesResponse.TYPE, RetrieveConnectionStatusesResponse::fromJson);

        return new AmqpBridgeCommandResponseRegistry(parseStrategies);
    }

}
