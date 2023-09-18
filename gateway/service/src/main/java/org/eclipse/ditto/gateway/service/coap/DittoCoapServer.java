/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.coap;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.elements.config.Configuration;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.Route;

final class DittoCoapServer extends CoapServer {

    private final DittoCoapResourceFacade dittoCoapResourceFacade;

    DittoCoapServer(final ActorSystem actorSystem,
            final Route rootRoute,
            final Configuration config,
            final int... ports) {

        super(config, ports);
        dittoCoapResourceFacade = new DittoCoapResourceFacade("api", actorSystem, rootRoute);
        getRoot().add(dittoCoapResourceFacade);
    }

    @Override
    public synchronized void destroy() {
        dittoCoapResourceFacade.shutdown();
        super.destroy();
    }

}
