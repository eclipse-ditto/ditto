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


import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Default creator of Props for Connections retrieval actors.
 */
public class DefaultConnectionsRetrievalActorPropsFactory implements ConnectionsRetrievalActorPropsFactory {

    public DefaultConnectionsRetrievalActorPropsFactory(final ActorSystem actorSystem, final Config config) {
        //NoOp Constructor to match extension instantiation
    }

    @Override
    public Props getActorProps(final ActorRef edgeCommandForwarder, final ActorRef sender) {
        return DittoConnectionsRetrievalActor.props(edgeCommandForwarder, sender);
    }
}
