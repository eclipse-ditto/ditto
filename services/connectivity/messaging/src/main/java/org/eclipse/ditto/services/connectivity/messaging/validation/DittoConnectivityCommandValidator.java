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
public class DittoConnectivityCommandValidator implements ConnectivityCommandInterceptor {

    private final ClientActorPropsFactory propsFactory;
    private final ActorRef conciergeForwarder;

    public DittoConnectivityCommandValidator(
            final ClientActorPropsFactory propsFactory, final ActorRef conciergeForwarder) {
        this.propsFactory = propsFactory;
        this.conciergeForwarder = conciergeForwarder;
    }

    @Override
    public void accept(final ConnectivityCommand<?> command) {
        switch (command.getType()) {
            case CreateConnection.TYPE:
            case TestConnection.TYPE:
            case ModifyConnection.TYPE:
                final Connection connection = getConnectionFromCommand(command);
                if (connection != null) {
                    propsFactory.getActorPropsForType(connection, conciergeForwarder);
                }
                break;
            default: //nothing to validate for other commands
        }
    }
}