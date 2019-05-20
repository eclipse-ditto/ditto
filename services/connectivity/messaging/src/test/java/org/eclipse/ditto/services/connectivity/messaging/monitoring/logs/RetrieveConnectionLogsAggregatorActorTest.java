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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
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
                            DEFAULT_TIMEOUT));

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
                            DEFAULT_TIMEOUT));

            responses.forEach(response -> underTest.tell(response, getRef()));

            sender.expectMsg(expectedResponse);
        }};
    }

    @Test
    public void withTimeoutAndWithoutMessages() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Connection connection = createConnectionWithClients(1);
            final Duration shortTimeout = Duration.ofMillis(10);

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            shortTimeout));


            sender.expectMsgClass(ConnectionTimeoutException.class);
        }};
    }

    @Test
    public void withTimeoutWithMessages() {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final Duration aggregatorTimeout = Duration.ofSeconds(5);
            final FiniteDuration expectMessageTimeout = FiniteDuration.apply(aggregatorTimeout.getSeconds() + 1, TimeUnit.SECONDS);

            // create connection with more clients than responses
            final Connection connection = createConnectionWithClients(2);

            final ActorRef underTest = childActorOf(
                    RetrieveConnectionLogsAggregatorActor.props(connection, sender.ref(), DITTO_HEADERS,
                            aggregatorTimeout));

            // only send one response
            final RetrieveConnectionLogsResponse expectedResponse =
                    createRetrieveConnectionLogsResponse(connection.getId(), Instant.now().minusSeconds(123),
                            Instant.now().plusSeconds(444));

            underTest.tell(expectedResponse, getRef());

            // expect that known response will be sent, ignoring missing responses
            sender.expectMsg(expectMessageTimeout, expectedResponse);
        }};
    }

    private Collection<RetrieveConnectionLogsResponse> createRetrieveConnectionLogsResponses(final int amount,
            final String connectionId, @Nullable final Instant enabledSince, @Nullable final Instant enabledUntil) {
        return Stream.iterate(0, i -> i + 1)
                .limit(amount)
                .map(unused -> createRetrieveConnectionLogsResponse(connectionId, enabledSince, enabledUntil))
                .collect(Collectors.toList());
    }

    private RetrieveConnectionLogsResponse createRetrieveConnectionLogsResponse(final String connectionId,
            @Nullable final Instant enabledSince, @Nullable final Instant enabledUntil) {
        return RetrieveConnectionLogsResponse.of(connectionId,
                TestConstants.Monitoring.LOG_ENTRIES,
                enabledSince,
                enabledUntil,
                DITTO_HEADERS);
    }

    private RetrieveConnectionLogsResponse createExpectedResponse(
            final Collection<RetrieveConnectionLogsResponse> clientResponses) {
        final Collection<LogEntry> mergedLogEntries = clientResponses.stream()
                .map(RetrieveConnectionLogsResponse::getConnectionLogs)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        final RetrieveConnectionLogsResponse firstResponse = clientResponses.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("collection should contain at least one response"));

        return RetrieveConnectionLogsResponse.of(firstResponse.getConnectionId(),
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
