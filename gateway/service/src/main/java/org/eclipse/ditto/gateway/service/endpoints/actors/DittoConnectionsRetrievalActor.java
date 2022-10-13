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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor for retrieving multiple connections.
 */
final class DittoConnectionsRetrievalActor extends AbstractConnectionsRetrievalActor {

    @SuppressWarnings("unused")
    private DittoConnectionsRetrievalActor(final ActorRef edgeCommandForwarder, final ActorRef sender) {
        super(edgeCommandForwarder, sender);
    }

    /**
     * Creates props for {@code ConnectionsRetrievalActor}.
     *
     * @param edgeCommandForwarder the edge command forwarder.
     * @param sender the initial sender.
     * @return the props.
     */
    public static Props props(final ActorRef edgeCommandForwarder, final ActorRef sender) {
        return Props.create(DittoConnectionsRetrievalActor.class, edgeCommandForwarder, sender);
    }

    @Override
    protected void retrieveConnections(final RetrieveConnections retrieveConnections) {
        retrieveAllConnectionsIds(retrieveConnections);
    }

}
