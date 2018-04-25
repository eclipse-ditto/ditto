/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.services.connectivity.messaging.MockConnectionActor.mockConnectionActorPropsFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import scala.Option;

public class TestConstants {

    public static final Config CONFIG = ConfigFactory.load("test");
    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectionStatus STATUS = ConnectionStatus.OPEN;
    private static final String URI_TEMPLATE = "amqps://username:password@%s:%s";
    public static final String SUBJECT_ID = "mySolutionId:mySubject";
    public static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance(SUBJECT_ID));
    private static final Set<Source> SOURCES = new HashSet<>(
            Arrays.asList(ConnectivityModelFactory.newSource(2, "amqp/source1"),
                    ConnectivityModelFactory.newSource(2, "amqp/source2")));
    private static final Set<Target> TARGETS = new HashSet<>(
            Collections.singletonList(ConnectivityModelFactory.newTarget("twinEventExchange/twinEventRoutingKey",
                    "_/_/things/twin/events")));
    public static final String THING_ID = "ditto:thing";
    private static final Thing THING = Thing.newBuilder().setId(THING_ID).build();
    public static final String CORRELATION_ID = "cid";

    public static String createRandomConnectionId() {
        return "connection-" + UUID.randomUUID();
    }

    public static String getUri(final ActorSystem actorSystem) {
        final String akkaHost =
                actorSystem.provider().getDefaultAddress().host().get();
        final Integer akkaPort = ((Integer) actorSystem.provider()
                .getDefaultAddress()
                .port().get());
        return String.format(URI_TEMPLATE, akkaHost, akkaPort);
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem) {
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, TYPE, STATUS, getUri(actorSystem), AUTHORIZATION_CONTEXT)
                .sources(SOURCES)
                .targets(TARGETS)
                .build();
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
                connectionActorPropsFactory);

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

    public static ThingModifiedEvent thingModified(final Collection<String> readSubjects) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().readSubjects(readSubjects).build();
        return ThingModified.of(THING, 1, dittoHeaders);
    }

    public static String modifyThing() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();
        final ModifyThing modifyThing = ModifyThing.of(THING_ID, THING, null, dittoHeaders);
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyThing);
        final JsonifiableAdaptable jsonifiable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        return jsonifiable.toJsonString();
    }

    private static void backOff(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
