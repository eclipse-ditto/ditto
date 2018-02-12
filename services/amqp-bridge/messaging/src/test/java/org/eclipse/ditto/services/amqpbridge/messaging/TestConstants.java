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
package org.eclipse.ditto.services.amqpbridge.messaging;

import static org.eclipse.ditto.services.amqpbridge.messaging.MockConnectionActor.mockConnectionActorPropsFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionType;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;

public class TestConstants {

    static final String PROXY_ACTOR_PATH = "/user/gatewayRoot/proxy";
    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final String URI = "amqps://username:password@my.endpoint:443";
    private static final String SUBJECT_ID = "mySolutionId:mySubject";
    private static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance(SUBJECT_ID);
    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("amqp/source1", "amqp/source2"));
    private static final boolean FAILOVER = false;
    private static final boolean VALIDATE_CERT = true;
    private static final int THROTTLE = 250;
    public static final Config CONFIG = ConfigFactory.load("test");

    public static String createRandomConnectionId() {
        return ConnectionType.AMQP_10.getName() + ":connection-" + UUID.randomUUID();
    }

    public static AmqpConnection createConnection(final String connectionId) {
        return AmqpBridgeModelFactory.newConnection(connectionId, TYPE, URI, AUTHORIZATION_SUBJECT, SOURCES, FAILOVER,
                VALIDATE_CERT, THROTTLE);
    }

    static ActorRef createConnectionSupervisorActor(final String connectionId, final ActorSystem actorSystem,
            final ActorRef pubSubMediator) {
        return createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator,
                mockConnectionActorPropsFactory);
    }

    static ActorRef createConnectionSupervisorActor(final String connectionId, final ActorSystem actorSystem,
            final ActorRef pubSubMediator, final ConnectionActorPropsFactory connectionActorPropsFactory) {
        final Duration minBackoff = Duration.ofSeconds(1);
        final Duration maxBackoff = Duration.ofSeconds(5);
        final Double randomFactor = 1.0;
        final Props props = ConnectionSupervisorActor.props(minBackoff, maxBackoff, randomFactor, pubSubMediator,
                PROXY_ACTOR_PATH, connectionActorPropsFactory);

        final int maxAttemps = 5;
        final long backoffMs = 1000L;

        for (int attempt = 1; ; ++attempt) {
            try {
                return actorSystem.actorOf(props, connectionId);
            } catch (final InvalidActorNameException invalidActorNameException) {
                if (attempt >= maxAttemps) {
                    throw invalidActorNameException;
                } else {
                    backOff(backoffMs);
                }
            }
        }
    }

    private static void backOff(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
