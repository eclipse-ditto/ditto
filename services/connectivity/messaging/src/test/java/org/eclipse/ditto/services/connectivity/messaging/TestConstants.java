/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static java.util.Arrays.asList;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newSource;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget;
import static org.eclipse.ditto.services.connectivity.messaging.MockClientActor.mockClientActorPropsFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

public class TestConstants {

    public static final Config CONFIG = ConfigFactory.load("test");
    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectionStatus STATUS = ConnectionStatus.OPEN;
    private static final String URI_TEMPLATE = "amqps://username:password@%s:%s";

    public static final String CORRELATION_ID = "cid";

    public static class Things {

        public static final String NAMESPACE = "ditto";
        public static final String ID = "thing";
        public static final String THING_ID = NAMESPACE + ":" + ID;
        public static final Thing THING = Thing.newBuilder().setId(THING_ID).build();
    }

    public static class Authorization {

        static final String SUBJECT_ID = "some:subject";
        static final String SOURCE_SUBJECT_ID = "source:subject";
        private static final String UNAUTHORIZED_SUBJECT_ID = "another:subject";
        public static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
                AuthorizationSubject.newInstance(SUBJECT_ID));
        public static final AuthorizationContext SOURCE_SPECIFIC_CONTEXT = AuthorizationContext.newInstance(
                AuthorizationSubject.newInstance(SOURCE_SUBJECT_ID));
        private static final AuthorizationContext UNAUTHORIZED_AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
                AuthorizationSubject.newInstance(UNAUTHORIZED_SUBJECT_ID));
    }

    public static class Sources {

        public static final List<Source> SOURCES_WITH_AUTH_CONTEXT =
                asList(newSource(2, 0, Authorization.SOURCE_SPECIFIC_CONTEXT, "amqp/source1"));
        public static final List<Source> SOURCES_WITH_SAME_ADDRESS =
                asList(newSource(1, 0, Authorization.SOURCE_SPECIFIC_CONTEXT, "source1"),
                        newSource(1, 1, Authorization.SOURCE_SPECIFIC_CONTEXT, "source1"));
    }

    public static class Targets {

        static final Target TARGET_WITH_PLACEHOLDER =
                newTarget("target:{{ thing:namespace }}/{{thing:name}}", Authorization.AUTHORIZATION_CONTEXT,
                        Topic.TWIN_EVENTS);
        static final Target TWIN_TARGET =
                newTarget("twinEventExchange/twinEventRoutingKey", Authorization.AUTHORIZATION_CONTEXT,
                        Topic.TWIN_EVENTS);
        private static final Target TWIN_TARGET_UNAUTHORIZED =
                newTarget("twin/key", Authorization.UNAUTHORIZED_AUTHORIZATION_CONTEXT, Topic.TWIN_EVENTS);
        private static final Target LIVE_TARGET =
                newTarget("live/key", Authorization.AUTHORIZATION_CONTEXT, Topic.LIVE_EVENTS);
        private static final Set<Target> TARGETS = asSet(TWIN_TARGET, TWIN_TARGET_UNAUTHORIZED, LIVE_TARGET);
    }

    public static final class Certificates {

        public static final String CA_CRT = getCert("ca.crt");
        // signed by CA_CRT
        // CN=localhost
        public static final String SERVER_KEY = getCert("server.key");
        public static final String SERVER_CRT = getCert("server.crt");

        // signed by CA_CRT
        // no CN
        public static final String CLIENT_KEY = getCert("client.key");
        public static final String CLIENT_CRT = getCert("client.crt");

        // signed by self
        // no CN
        public static final String CLIENT_SELF_SIGNED_KEY = getCert("client-self-signed.key");
        public static final String CLIENT_SELF_SIGNED_CRT = getCert("client-self-signed.crt");

        // AWS IoT CAs and server certificate
        public static final String AWS_CA_CRT = getCert("aws-ca.pem");
        public static final String AWS_IOT_CRT = getCert("aws-iot.crt");

        // signed by CA_CRT with common name (CN) and alternative names.
        // CN=server.alt
        // subjectAltNames=
        //   DNS:example.com
        //   IP:100:0:0:0:1319:8a2e:370:7348,
        //   IP:127.128.129.130
        public static final String SERVER_WITH_ALT_NAMES_KEY = getCert("server-alt.key");
        public static final String SERVER_WITH_ALT_NAMES_CRT = getCert("server-alt.crt");

        private static String getCert(final String cert) {
            final String path = "/certificates/" + cert;
            try (final InputStream inputStream = Certificates.class.getResourceAsStream(path)) {
                final Scanner scanner = new Scanner(inputStream, StandardCharsets.US_ASCII.name()).useDelimiter("\\A");
                return scanner.next();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

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
        return createConnection(connectionId, actorSystem, Sources.SOURCES_WITH_AUTH_CONTEXT);
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final List<Source> sources) {
        return createConnection(connectionId, actorSystem, STATUS, sources);
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final ConnectionStatus status, final List<Source> sources) {
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, TYPE, status, getUri(actorSystem))
                .sources(sources)
                .targets(Targets.TARGETS)
                .build();
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final Target... targets) {
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, TYPE, STATUS, getUri(actorSystem))
                .sources(Sources.SOURCES_WITH_AUTH_CONTEXT)
                .targets(asSet(targets))
                .build();
    }

    @SafeVarargs
    public static <T> Set<T> asSet(final T... array) {
        return new HashSet<>(asList(array));
    }

    static ActorRef createConnectionSupervisorActor(final String connectionId, final ActorSystem actorSystem,
            final ActorRef pubSubMediator, final ActorRef conciergeForwarder) {
        return createConnectionSupervisorActor(connectionId, actorSystem, pubSubMediator, conciergeForwarder,
                mockClientActorPropsFactory);
    }

    static ActorRef createConnectionSupervisorActor(final String connectionId, final ActorSystem actorSystem,
            final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final ClientActorPropsFactory clientActorPropsFactory) {
        final Duration minBackoff = Duration.ofSeconds(1);
        final Duration maxBackoff = Duration.ofSeconds(5);
        final Double randomFactor = 1.0;
        final Props props = ConnectionSupervisorActor.props(minBackoff, maxBackoff, randomFactor, pubSubMediator,
                conciergeForwarder, clientActorPropsFactory, null);

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
        return ThingModified.of(Things.THING, 1, dittoHeaders);
    }

    public static MessageCommand sendThingMessage(final Collection<String> readSubjects) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .readSubjects(readSubjects)
                .channel(TopicPath.Channel.LIVE.getName())
                .build();
        final Message<Object> message =
                Message.newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, Things.THING_ID, "ditto").build())
                        .build();
        return SendThingMessage.of(Things.THING_ID, message, dittoHeaders);
    }

    public static String signalToDittoProtocolJsonString(final Signal<?> signal) {
        final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(signal);
        final JsonifiableAdaptable jsonifiable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        return jsonifiable.toJsonString();
    }

    public static String modifyThing() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).putHeader(
                ExternalMessage.REPLY_TO_HEADER, "replies").build();
        final ModifyThing modifyThing = ModifyThing.of(Things.THING_ID, Things.THING, null, dittoHeaders);
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

    public static class ConciergeForwarderActorMock extends AbstractActor {

        private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

        private ConciergeForwarderActorMock() {
        }

        public static Props props() {
            return Props.create(ConciergeForwarderActorMock.class,
                    (Creator<ConciergeForwarderActorMock>) ConciergeForwarderActorMock::new);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(o -> log.info("Received: ''{}'' from ''{}''", o, getSender()))
                    .build();
        }
    }
}
