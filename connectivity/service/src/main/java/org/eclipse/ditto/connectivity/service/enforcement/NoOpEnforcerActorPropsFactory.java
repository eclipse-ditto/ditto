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
package org.eclipse.ditto.connectivity.service.enforcement;

import org.eclipse.ditto.connectivity.model.ConnectionId;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public final class NoOpEnforcerActorPropsFactory implements ConnectionEnforcerActorPropsFactory {

    NoOpEnforcerActorPropsFactory(final ActorSystem actorSystem, final Config config) {
        //NoOp Constructor to match extension initialization
    }

    @Override
    public Props get(final ConnectionId connectionId) {
        return NoOpEnforcerActor.props();
    }

    private static final class NoOpEnforcerActor extends AbstractActor {


        private static Props props() {
            return Props.create(NoOpEnforcerActor.class);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(any -> sender().tell(any, ActorRef.noSender()))
                    .build();
        }

    }

}
