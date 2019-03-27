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
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.model.connectivity.Connection;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public interface ClientActorPropsFactory {

    /**
     * Create actor {@link Props} for a connection.
     *
     * @param connection the connection
     * @param conciergeForwarder the actor used to send signals to the concierge service
     * @return the actor props
     */
    Props getActorPropsForType(Connection connection, ActorRef conciergeForwarder);

}
