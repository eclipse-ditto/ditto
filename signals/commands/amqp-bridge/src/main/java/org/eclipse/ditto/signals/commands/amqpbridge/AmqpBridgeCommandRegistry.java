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
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatuses;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link AmqpBridgeCommand}s.
 */
@Immutable
public final class AmqpBridgeCommandRegistry extends AbstractCommandRegistry<AmqpBridgeCommand> {

    private AmqpBridgeCommandRegistry(final Map<String, JsonParsable<AmqpBridgeCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code AmqpBridgeCommandRegistry}.
     *
     * @return the command registry.
     */
    public static AmqpBridgeCommandRegistry newInstance() {
        final Map<String, JsonParsable<AmqpBridgeCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreateConnection.TYPE, CreateConnection::fromJson);
        parseStrategies.put(DeleteConnection.TYPE, DeleteConnection::fromJson);
        parseStrategies.put(OpenConnection.TYPE, OpenConnection::fromJson);
        parseStrategies.put(CloseConnection.TYPE, CloseConnection::fromJson);

        parseStrategies.put(RetrieveConnection.TYPE, RetrieveConnection::fromJson);
        parseStrategies.put(RetrieveConnectionStatus.TYPE, RetrieveConnectionStatus::fromJson);
        parseStrategies.put(RetrieveConnectionStatuses.TYPE, RetrieveConnectionStatuses::fromJson);

        return new AmqpBridgeCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return AmqpBridgeCommand.TYPE_PREFIX;
    }

}
