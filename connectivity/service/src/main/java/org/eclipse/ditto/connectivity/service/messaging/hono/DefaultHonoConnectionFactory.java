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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import akka.actor.ActorSystem;

public class DefaultHonoConnectionFactory extends HonoConnectionFactory {

    private DefaultHonoConnectionFactory(final ActorSystem actorSystem, final Connection connection) {
        super(actorSystem, connection);
    }

    @Override
    public UserPasswordCredentials getCredentials() {
        return honoConfig.getUserPasswordCredentials();
    }

    @Override
    protected String getTenantId() {
        return "";
    }

    public static DefaultHonoConnectionFactory getInstance(final ActorSystem actorSystem, final Connection connection) {
        return new DefaultHonoConnectionFactory(actorSystem, connection);
    }
    public static Connection getEnrichedConnection(ActorSystem actorSystem, Connection connection) {
        return new DefaultHonoConnectionFactory(actorSystem, connection).enrichConnection();
    }
}
