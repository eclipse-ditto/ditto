/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.connectivity.model.AddressMetric;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import junit.framework.AssertionFailedError;

/**
 * Tests {@link UsageBasedPriorityProvider}.
 */
public final class UsageBasedPriorityProviderTest {

    private static ActorSystem system;

    private final ConnectionMetrics connectionMetrics;
    private UsageBasedPriorityProvider underTest;
    private TestProbe connectionPersistenceActorProbe;
    private DittoDiagnosticLoggingAdapter mockLog;

    public UsageBasedPriorityProviderTest() {
        final Measurement inboundConsumedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true, Map.of(Duration.ofHours(24), 10L),
                        null);
        final Measurement outboundPublishedMeasurement =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true, Map.of(Duration.ofHours(24), 10L),
                        null);
        final AddressMetric inbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(inboundConsumedMeasurement));
        final AddressMetric outbound =
                ConnectivityModelFactory.newAddressMetric(Set.of(outboundPublishedMeasurement));
        connectionMetrics = ConnectivityModelFactory.newConnectionMetrics(inbound, outbound);
    }

    @BeforeClass
    public static void setupClass() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        system = null;
    }

    @Before
    public void setup() {
        connectionPersistenceActorProbe = TestProbe.apply(system);
        mockLog = mock(DittoDiagnosticLoggingAdapter.class);
        when(mockLog.withCorrelationId(anyString())).thenReturn(mockLog);
        underTest = UsageBasedPriorityProvider.getInstance(connectionPersistenceActorProbe.ref(), mockLog);
    }

    @Test
    public void getPriority() {
        final CompletionStage<Integer> futurePriority =
                underTest.getPriorityFor(ConnectionId.generateRandom(), "test-getPriority");
        new TestKit(system) {{
            final RetrieveConnectionMetrics retrieveConnectionMetrics =
                    connectionPersistenceActorProbe.expectMsgClass(RetrieveConnectionMetrics.class);
            connectionPersistenceActorProbe.reply(
                    RetrieveConnectionMetricsResponse.of(retrieveConnectionMetrics.getEntityId(),
                            JsonObject.newBuilder().set("connectionMetrics", connectionMetrics.toJson()).build(),
                            retrieveConnectionMetrics.getDittoHeaders()));
        }};
        assertPriority(futurePriority, 20);
        verifyNoInteractions(mockLog);
    }

    @Test
    public void getPriorityWithException() {
        final CompletionStage<Integer> futurePriority =
                underTest.getPriorityFor(ConnectionId.generateRandom(), "test-getPriority");
        new TestKit(system) {{
            connectionPersistenceActorProbe.expectMsgClass(RetrieveConnectionMetrics.class);
            // No response causes ask timeout exception.
        }};
        assertPriority(futurePriority, 0);
        verify(mockLog).warning(eq("Got error when trying to retrieve the connection metrics: <{}>"), any());
    }

    @Test
    public void getPriorityWithInvalidResponse() {
        final CompletionStage<Integer> futurePriority =
                underTest.getPriorityFor(ConnectionId.generateRandom(), "test-getPriority");
        new TestKit(system) {{
            connectionPersistenceActorProbe.expectMsgClass(RetrieveConnectionMetrics.class);
            connectionPersistenceActorProbe.reply("Strange unexpected message");
        }};
        assertPriority(futurePriority, 0);
        verify(mockLog).warning(eq("Expected <{}> but got <{}>"), any(), any());
    }

    private static void assertPriority(final CompletionStage<Integer> futurePriority,
            final Integer expectedPriority) {
        try {
            final Integer actualPriority = futurePriority.toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(actualPriority).isEqualTo(expectedPriority);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            throw new AssertionFailedError(
                    MessageFormat.format("Expected priority <{0}> but got exception: <{1}>", expectedPriority,
                            futurePriority));
        }
    }


}
