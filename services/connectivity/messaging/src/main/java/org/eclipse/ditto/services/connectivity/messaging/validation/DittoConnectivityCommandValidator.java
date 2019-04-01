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

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;

import akka.actor.ActorRef;

/**
 * Checks if the given {@link ConnectivityCommand} is valid by trying to create the client actor props.
 */
public final class DittoConnectivityCommandValidator implements ConnectivityCommandInterceptor {

    private final ClientActorPropsFactory propsFactory;
    private final ActorRef conciergeForwarder;
    private final ConnectionValidator connectionValidator;

    public DittoConnectivityCommandValidator(
            final ClientActorPropsFactory propsFactory, final ActorRef conciergeForwarder,
            final ConnectionValidator connectionValidator) {
        this.propsFactory = propsFactory;
        this.conciergeForwarder = conciergeForwarder;
        this.connectionValidator = connectionValidator;
    }

    @Override
    public void accept(final ConnectivityCommand<?> command) {
        switch (command.getType()) {
            case CreateConnection.TYPE:
            case TestConnection.TYPE:
            case ModifyConnection.TYPE:
                final Connection connection = getConnectionFromCommand(command);
                if (connection != null) {
                    connectionValidator.validate(connection, command.getDittoHeaders());
                    propsFactory.getActorPropsForType(connection, conciergeForwarder);
                } else {
                    // should never happen
                    throw new IllegalStateException("connection=null in " + command);
                }
                break;
            default: //nothing to validate for other commands
        }
    }
}
