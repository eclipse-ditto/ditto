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
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.TestProbe;

public final class MockClientActorPropsFactory implements ClientActorPropsFactory {

    public static TestProbe mockClientActorProbe;
    public static TestProbe gossipProbe;

    public MockClientActorPropsFactory(final ActorSystem actorSystem, final Config config) {}

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor, final ActorSystem actorSystem, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        final Boolean shouldThrowException = Optional.ofNullable(dittoHeaders.get("should-throw-exception"))
                .map(Boolean::parseBoolean)
                .orElse(false);
        if (shouldThrowException) {
            throw ConnectionConfigurationInvalidException.newBuilder("validation failed...").build();
        }
        return Props.create(MockClientActor.class, dittoHeaders);
    }

    public static class MockClientActor extends AbstractActor {

        private int current = 0;

        public MockClientActor(final DittoHeaders dittoHeaders) {
            final int numberOfFailures = Optional.ofNullable(dittoHeaders.get("number-of-instantiation-failures"))
                    .map(Integer::parseInt)
                    .orElse(0);
            if (current++ < numberOfFailures) {
                final var message =
                        MessageFormat.format("''FailingActor'' intentionally failing for {0} of {1} times",
                                current, numberOfFailures);
                throw new IllegalStateException(message);
            }
        }

        @Override
        public void preStart() {
            gossipProbe.ref().tell(self(), self());
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(WithDittoHeaders.class,
                            message -> mockClientActorProbe.ref().tell(WithSender.of(message, getSelf()), getSender()))
                    .matchAny(any -> mockClientActorProbe.ref().forward(any, getContext()))
                    .build();
        }
    }

}
