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
package org.eclipse.ditto.connectivity.service.messaging;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * A {@link ClientActorPropsFactory} which provides an actor which will throw an exception {@code
 * retriesUntilSuccess} times in its constructor, before it starts up normally.
 */
public final class FailingActorProvider implements ClientActorPropsFactory {

    private final int retriesUntilSuccess;
    private int current = 0;

    public FailingActorProvider(final ActorSystem actorSystem) {
        retriesUntilSuccess = actorSystem.settings().config().getInt("failingRetries");
    }

    @Override
    public Props getActorPropsForType(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorSystem system,
            final DittoHeaders dittoHeaders,
            final Config overwrites) {

        return Props.create(FailingActorProvider.FailingActor.class, FailingActorProvider.FailingActor::new);
    }


    private final class FailingActor extends AbstractActor {

        FailingActor() {
            if (current++ < retriesUntilSuccess) {
                final var message =
                        MessageFormat.format("''FailingActor'' intentionally failing for {0} of {1} times",
                                current, retriesUntilSuccess);
                throw new IllegalStateException(message);
            }
        }

        @Override
        public void preStart() {
            System.out.println("'FailingActor' finally started without exception.");
        }

        @Override
        public void postStop() {
            System.out.println("'FailingActor' stopped.");
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().build();
        }

    }

}
