/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link RetrieveConnectionLogsAggregatorActor}.
 */
public final class RetrieveConnectionLogsAggregatorActorTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static ActorSystem actorSystem;
    private static final MonitoringLoggerConfig LOGGER_CONFIG =
            TestConstants.CONNECTIVITY_CONFIG.getMonitoringConfig().logger();

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void withOneClient() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Connection connection = createConnectionWithClients(1);

            final RetrieveConnectionLogsResponse expectedResponse =
                    createRetrieveConnectionLogsResponse(connection.getId(), Instant.now().minusSeconds(123),
                            Instant.now().plusSeconds(444));

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            DEFAULT_TIMEOUT, LOGGER_CONFIG.maxLogSizeInBytes()));

            underTest.tell(expectedResponse, getRef());

            sender.expectMsg(expectedResponse);
        }};
    }

    @Test
    public void withMultipleClients() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Connection connection = createConnectionWithClients(3);

            final Instant since = Instant.now().minusSeconds(333);
            final Instant until = Instant.now().plusSeconds(555);
            final Collection<RetrieveConnectionLogsResponse> responses = Arrays.asList(
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until),
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until),
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until)
            );
            final RetrieveConnectionLogsResponse expectedResponse = createExpectedResponse(responses);

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            DEFAULT_TIMEOUT, LOGGER_CONFIG.maxLogSizeInBytes()));

            responses.forEach(response -> underTest.tell(response, getRef()));

            sender.expectMsg(expectedResponse);
        }};
    }

    @Test
    public void withMultipleClientsRespectsMaxLogSize() throws InterruptedException {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Connection connection = createConnectionWithClients(3);

            final Instant since = Instant.now().minusSeconds(333);
            final Instant until = Instant.now().plusSeconds(555);
            final Collection<RetrieveConnectionLogsResponse> responses = Arrays.asList(
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until, maxSizeLogEntries()),
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until, maxSizeLogEntries()),
                    createRetrieveConnectionLogsResponse(connection.getId(), since, until, maxSizeLogEntries())
            );

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            DEFAULT_TIMEOUT, LOGGER_CONFIG.maxLogSizeInBytes()));

            responses.forEach(response -> underTest.tell(response, getRef()));

            final RetrieveConnectionLogsResponse retrieveConnectionLogsResponse =
                    sender.expectMsgClass(RetrieveConnectionLogsResponse.class);
            assertThat((Long) retrieveConnectionLogsResponse.getConnectionLogs().stream()
                    .map(LogEntry::toJsonString)
                    .map(String::length)
                    .mapToLong(Integer::intValue)
                    .sum())
                    .isLessThan(LOGGER_CONFIG.maxLogSizeInBytes());
        }};
    }

    private static Collection<LogEntry> maxSizeLogEntries() throws InterruptedException {
        final List<LogEntry> logEntries = new ArrayList<>();

        int currentSize = 0;
        boolean maxSizeReached = false;
        while (!maxSizeReached) {
            final LogEntry logEntry =
                    ConnectivityModelFactory.newLogEntryBuilder(correlationId(), Instant.now(), LogCategory.TARGET,
                            LogType.DROPPED,
                            LogLevel.SUCCESS,
                            "Some example message that can be logged repeatedly just to create a big log.")
                            .address("some/address")
                            .entityId(ThingId.generateRandom())
                            .build();

            final int newSize = currentSize + logEntry.toJsonString().length();
            maxSizeReached = newSize > LOGGER_CONFIG.maxLogSizeInBytes();
            if (!maxSizeReached) {
                logEntries.add(logEntry);
                currentSize = newSize;
            }
            TimeUnit.MILLISECONDS.sleep(1); //ensure timestamps of logs are all different.
        }

        return logEntries;
    }

    private static String correlationId() {
        return UUID.randomUUID().toString();
    }

    @Test
    public void withTimeoutAndWithoutMessages() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Connection connection = createConnectionWithClients(1);
            final Duration shortTimeout = Duration.ofMillis(10);

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            shortTimeout, LOGGER_CONFIG.maxLogSizeInBytes()));


            sender.expectMsgClass(ConnectionTimeoutException.class);
        }};
    }

    @Test
    public void withTimeoutWithMessages() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Duration aggregatorTimeout = Duration.ofSeconds(5);
            final FiniteDuration expectMessageTimeout =
                    FiniteDuration.apply(aggregatorTimeout.getSeconds() + 1, TimeUnit.SECONDS);

            // create connection with more clients than responses
            final Connection connection = createConnectionWithClients(2);

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            aggregatorTimeout, LOGGER_CONFIG.maxLogSizeInBytes()));

            // only send one response
            final RetrieveConnectionLogsResponse expectedResponse =
                    createRetrieveConnectionLogsResponse(connection.getId(), Instant.now().minusSeconds(123),
                            Instant.now().plusSeconds(444));

            underTest.tell(expectedResponse, getRef());

            // expect that known response will be sent, ignoring missing responses
            sender.expectMsg(expectMessageTimeout, expectedResponse);
        }};
    }

    private RetrieveConnectionLogsResponse createRetrieveConnectionLogsResponse(final ConnectionId connectionId,
            @Nullable final Instant enabledSince, @Nullable final Instant enabledUntil) {
        return createRetrieveConnectionLogsResponse(connectionId, enabledSince, enabledUntil,
                TestConstants.Monitoring.LOG_ENTRIES);
    }

    private RetrieveConnectionLogsResponse createRetrieveConnectionLogsResponse(final ConnectionId connectionId,
            @Nullable final Instant enabledSince, @Nullable final Instant enabledUntil,
            final Collection<LogEntry> logEntries) {

        return RetrieveConnectionLogsResponse.of(connectionId,
                logEntries,
                enabledSince,
                enabledUntil,
                DITTO_HEADERS);
    }

    private RetrieveConnectionLogsResponse createExpectedResponse(
            final Collection<RetrieveConnectionLogsResponse> clientResponses) {
        final Collection<LogEntry> mergedLogEntries = clientResponses.stream()
                .map(RetrieveConnectionLogsResponse::getConnectionLogs)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(LogEntry::getTimestamp))
                .toList();
        final RetrieveConnectionLogsResponse firstResponse = clientResponses.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("collection should contain at least one response"));

        return RetrieveConnectionLogsResponse.of(firstResponse.getEntityId(),
                mergedLogEntries,
                firstResponse.getEnabledSince().orElse(null),
                firstResponse.getEnabledUntil().orElse(null),
                firstResponse.getDittoHeaders());
    }

    private Connection createConnectionWithClients(final int clients) {
        return TestConstants.createConnection(TestConstants.createRandomConnectionId())
                .toBuilder()
                .clientCount(clients)
                .build();
    }

}
