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
import static java.util.Collections.singletonList;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget;
import static org.eclipse.ditto.services.connectivity.messaging.MockClientActor.mockClientActorPropsFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
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
import org.eclipse.ditto.services.base.DittoServiceTng;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultReconnectConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ReconnectConfig;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
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
import akka.event.Logging;
import akka.japi.Creator;

public final class TestConstants {

    // concurrent mutable collection of all running mock servers
    private static final AbstractQueue<ServerSocket> MOCK_SERVERS = new ConcurrentLinkedQueue<>();

    public static final Config CONFIG = ConfigFactory.load("test");

    public static final MappingConfig MAPPING_CONFIG;
    public static final ConnectionConfig CONNECTION_CONFIG;
    public static final ClientConfig CLIENT_CONFIG;
    public static final ReconnectConfig RECONNECT_CONFIG;
    public static final ProtocolConfig PROTOCOL_CONFIG;

    static {
        final DefaultScopedConfig dittoScopedConfig =
                DefaultScopedConfig.newInstance(CONFIG, DittoServiceTng.DITTO_CONFIG_PATH);
        final DefaultScopedConfig connectivityScopedConfig =
                DefaultScopedConfig.newInstance(dittoScopedConfig, "connectivity");

        MAPPING_CONFIG = DefaultMappingConfig.of(connectivityScopedConfig);
        CONNECTION_CONFIG = DefaultConnectionConfig.of(connectivityScopedConfig);
        CLIENT_CONFIG = DefaultClientConfig.of(connectivityScopedConfig);
        RECONNECT_CONFIG = DefaultReconnectConfig.of(connectivityScopedConfig);
        PROTOCOL_CONFIG = DefaultProtocolConfig.of(dittoScopedConfig);
    }

    private static final ConnectionType TYPE = ConnectionType.AMQP_10;
    private static final ConnectivityStatus STATUS = ConnectivityStatus.OPEN;
    private static final String URI_TEMPLATE = "amqps://username:password@%s:%s";

    public static final String CORRELATION_ID = "cid";

    /**
     * Disable logging for 1 test to hide stacktrace or other logs on level ERROR. Comment out to debug the test.
     */
    public static void disableLogging(final ActorSystem system) {
        system.eventStream().setLogLevel(Logging.levelFor("off").map(Logging.LogLevel::asInt).get());
    }

    public static final HeaderMapping HEADER_MAPPING;

    static {
        final Map<String, String> map = new HashMap<>();
        map.put("eclipse", "ditto");
        map.put("thing_id", "{{ thing:id }}");
        map.put("device_id", "{{ header:device_id }}");
        map.put("prefixed_thing_id", "some.prefix.{{ thing:id }}");
        map.put("suffixed_thing_id", "{{ header:device_id }}.some.suffix");
        HEADER_MAPPING = ConnectivityModelFactory.newHeaderMapping(map);
    }

    public static final Instant INSTANT = Instant.now();

    public static final class Things {

        public static final String NAMESPACE = "ditto";
        public static final String ID = "thing";
        public static final String THING_ID = NAMESPACE + ":" + ID;
        public static final Thing THING = Thing.newBuilder().setId(THING_ID).build();

    }

    public static final class Authorization {

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

    public static final class Sources {

        public static final List<Source> SOURCES_WITH_AUTH_CONTEXT =
                singletonList(ConnectivityModelFactory.newSourceBuilder()
                        .address("amqp/source1")
                        .authorizationContext(Authorization.SOURCE_SPECIFIC_CONTEXT)
                        .consumerCount(2)
                        .index(0)
                        .build());
        public static final List<Source> SOURCES_WITH_SAME_ADDRESS =
                asList(ConnectivityModelFactory.newSourceBuilder()
                                .address("source1")
                                .authorizationContext(Authorization.SOURCE_SPECIFIC_CONTEXT)
                                .consumerCount(1)
                                .index(0)
                                .build(),
                        ConnectivityModelFactory.newSourceBuilder()
                                .address("source1")
                                .authorizationContext(Authorization.SOURCE_SPECIFIC_CONTEXT)
                                .consumerCount(1)
                                .index(1)
                                .build());
    }

    public static final class Targets {

        private static final HeaderMapping HEADER_MAPPING = null;

        public static final Target TARGET_WITH_PLACEHOLDER =
                newTarget("target:{{ thing:namespace }}/{{thing:name}}@{{ topic:channel }}",
                        Authorization.AUTHORIZATION_CONTEXT, HEADER_MAPPING,
                        null, Topic.TWIN_EVENTS);
        static final Target TWIN_TARGET =
                newTarget("twinEventExchange/twinEventRoutingKey", Authorization.AUTHORIZATION_CONTEXT, HEADER_MAPPING,
                        null, Topic.TWIN_EVENTS);
        private static final Target TWIN_TARGET_UNAUTHORIZED =
                newTarget("twin/key", Authorization.UNAUTHORIZED_AUTHORIZATION_CONTEXT, HEADER_MAPPING, null,
                        Topic.TWIN_EVENTS);
        private static final Target LIVE_TARGET =
                newTarget("live/key", Authorization.AUTHORIZATION_CONTEXT, HEADER_MAPPING, null, Topic.LIVE_EVENTS);
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

    public static final class Metrics {

        private static final Instant LAST_MESSAGE_AT = Instant.now();

        public static final String ID = "myConnectionId";

        public static final Duration ONE_MINUTE = Duration.ofMinutes(1);
        public static final Duration ONE_HOUR = Duration.ofHours(1);
        public static final Duration ONE_DAY = Duration.ofDays(1);
        public static final Duration[] DEFAULT_INTERVALS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

        public static final Map<Duration, Long> SOURCE_COUNTERS = asMap(
                entry(ONE_MINUTE, ONE_MINUTE.getSeconds()),
                entry(ONE_HOUR, ONE_HOUR.getSeconds()),
                entry(ONE_DAY, ONE_DAY.getSeconds()));
        public static final Measurement INBOUND =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true, SOURCE_COUNTERS, LAST_MESSAGE_AT);
        public static final Measurement FAILED_INBOUND =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true, SOURCE_COUNTERS, LAST_MESSAGE_AT);
        public static final Map<Duration, Long> TARGET_COUNTERS = asMap(
                entry(ONE_MINUTE, ONE_MINUTE.toMillis()),
                entry(ONE_HOUR, ONE_HOUR.toMillis()),
                entry(ONE_DAY, ONE_DAY.toMillis()));
        public static final Measurement OUTBOUND =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true, TARGET_COUNTERS, LAST_MESSAGE_AT);
        public static final Map<Duration, Long> MAPPING_COUNTERS = asMap(
                entry(ONE_MINUTE, ONE_MINUTE.toMinutes()),
                entry(ONE_HOUR, ONE_HOUR.toMinutes()),
                entry(ONE_DAY, ONE_DAY.toMinutes()));
        public static final Measurement MAPPING =
                ConnectivityModelFactory.newMeasurement(MetricType.MAPPED, true, MAPPING_COUNTERS, LAST_MESSAGE_AT);
        public static final Measurement FAILED_MAPPING =
                ConnectivityModelFactory.newMeasurement(MetricType.MAPPED, false, MAPPING_COUNTERS, LAST_MESSAGE_AT);

        public static final AddressMetric
                INBOUND_METRIC = ConnectivityModelFactory.newAddressMetric(asSet(INBOUND, MAPPING));
        public static final AddressMetric OUTBOUND_METRIC =
                ConnectivityModelFactory.newAddressMetric(asSet(MAPPING, OUTBOUND));

        public static final SourceMetrics SOURCE_METRICS1 = ConnectivityModelFactory.newSourceMetrics(
                asMap(entry("source1", INBOUND_METRIC), entry("source2", INBOUND_METRIC)));
        public static final TargetMetrics TARGET_METRICS1 = ConnectivityModelFactory.newTargetMetrics(
                asMap(entry("target1", OUTBOUND_METRIC), entry("target2", OUTBOUND_METRIC)));
        public static final ConnectionMetrics CONNECTION_METRICS1 = ConnectivityCounterRegistry
                .aggregateConnectionMetrics(SOURCE_METRICS1, TARGET_METRICS1);

        public static final RetrieveConnectionMetricsResponse METRICS_RESPONSE1 = RetrieveConnectionMetricsResponse
                .of(ID, CONNECTION_METRICS1, SOURCE_METRICS1, TARGET_METRICS1, DittoHeaders.empty());

        public static final SourceMetrics SOURCE_METRICS2 = ConnectivityModelFactory.newSourceMetrics(
                asMap(entry("source2", INBOUND_METRIC), entry("source3", INBOUND_METRIC)));
        public static final TargetMetrics TARGET_METRICS2 = ConnectivityModelFactory.newTargetMetrics(
                asMap(entry("target2", OUTBOUND_METRIC), entry("target3", OUTBOUND_METRIC)));
        public static final ConnectionMetrics CONNECTION_METRICS2 = ConnectivityCounterRegistry
                .aggregateConnectionMetrics(SOURCE_METRICS2, TARGET_METRICS2);

        public static final RetrieveConnectionMetricsResponse METRICS_RESPONSE2 = RetrieveConnectionMetricsResponse
                .of(ID, CONNECTION_METRICS2, SOURCE_METRICS2, TARGET_METRICS2, DittoHeaders.empty());

        public static Measurement mergeMeasurements(final MetricType type,
                final boolean success,
                final Measurement measurements,
                final int times) {

            final Map<Duration, Long> result = new HashMap<>();
            for (final Duration interval : DEFAULT_INTERVALS) {
                result.put(interval,
                        Optional.of(measurements)
                                .filter(m -> Objects.equals(type, m.getMetricType()))
                                .filter(m -> Objects.equals(success, m.isSuccess()))
                                .map(Measurement::getCounts)
                                .map(m -> m.getOrDefault(interval, 0L))
                                .orElse(0L) * times
                );
            }
            return ConnectivityModelFactory.newMeasurement(type, success, result, Metrics.LAST_MESSAGE_AT);
        }
    }

    private static <K, V> Map.Entry<K, V> entry(final K interval, final V count) {
        return new AbstractMap.SimpleImmutableEntry<>(interval, count);
    }

    @SafeVarargs
    private static <K, V> Map<K, V> asMap(final Map.Entry<K, V>... entries) {
        return Stream.of(entries).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static String createRandomConnectionId() {
        return "connection-" + UUID.randomUUID();
    }

    /**
     * Mock a listener on the server socket to fool connection client actors
     * into not failing the connections immediately. Close the server socket to stop the mock server.
     *
     * @return server socket of the mock server.
     */
    public static ServerSocket newMockServer() {
        final ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(0);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        Executors.newSingleThreadExecutor().submit(() -> {
            // accept many client sockets
            while (true) {
                try (final Socket clientSocket = serverSocket.accept()) {
                    // close socket immediately
                } catch (final IOException e) {
                    // break the loop
                    throw new IllegalStateException(e);
                }
            }
        });
        return serverSocket;
    }

    /**
     * Create a mock connection URI to a local server socket.
     *
     * @param serverSocket the server socket to connect to.
     * @return the mock connection URI.
     */
    public static String getUriOfMockServer(final ServerSocket serverSocket) {
        final int localPort = serverSocket.getLocalPort();
        return String.format(URI_TEMPLATE, "127.0.0.1", localPort);
    }

    /**
     * Create a mock connection URI and start a mock server on the same port.
     * Stop the mock servers by calling {@code stopMockServers()}.
     */
    public static String getUriOfNewMockServer() {
        final ServerSocket serverSocket = newMockServer();
        MOCK_SERVERS.add(serverSocket);
        return getUriOfMockServer(serverSocket);
    }

    /**
     * Stop mock servers started by the unit tests to release resources.
     */
    public static void stopMockServers() {
        MOCK_SERVERS.forEach(serverSocket -> {
            try {
                serverSocket.close();
            } catch (final IOException e) {
                // don't care, close remaining sockets anyway
            }
        });
        MOCK_SERVERS.clear();
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem) {
        return createConnection(connectionId, actorSystem, Sources.SOURCES_WITH_AUTH_CONTEXT);
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final List<Source> sources) {

        return createConnection(connectionId, actorSystem, STATUS, sources);
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final ConnectivityStatus status, final List<Source> sources) {
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, TYPE, status, getUriOfNewMockServer())
                .sources(sources)
                .targets(Targets.TARGETS)
                .build();
    }

    public static Connection createConnection(final String connectionId, final ActorSystem actorSystem,
            final Target... targets) {

        return ConnectivityModelFactory.newConnectionBuilder(connectionId, TYPE, STATUS, getUriOfNewMockServer())
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

    static ActorRef createConnectionSupervisorActor(final String connectionId,
            final ActorSystem actorSystem,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory clientActorPropsFactory) {

        final Props props = ConnectionSupervisorActor.props(CONNECTION_CONFIG, pubSubMediator, conciergeForwarder,
                clientActorPropsFactory, null);

        final int maxAttempts = 5;
        final long backOffMs = 1000L;

        for (int attempt = 1; ; ++attempt) {
            try {
                return actorSystem.actorOf(props, connectionId);
            } catch (final InvalidActorNameException invalidActorNameException) {
                if (attempt >= maxAttempts) {
                    throw invalidActorNameException;
                } else {
                    backOff(backOffMs);
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

    public static <T> Map.Entry<String, T> header(final String key, final T value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    public static final class ConciergeForwarderActorMock extends AbstractActor {

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
