/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsAggregationPersistence;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

public class AggregateThingsMetricsActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static ActorSystem system = ActorSystem.create();

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testHandleAggregateThingsMetrics() {
        new TestKit(system) {{

            // Create a mock persistence object
            ThingsAggregationPersistence mockPersistence = mock(ThingsAggregationPersistence.class);
            doReturn(Source.from(List.of(
                    new Document("_id", new Document(Map.of("_revision", 1L, "location", "Berlin")))
                            .append("online", 6)
                            .append("offline", 0),
                    new Document("_id", new Document(Map.of("_revision", 1L, "location", "Immenstaad")))
                            .append("online", 5)
                            .append("offline", 0),
                    new Document("_id", new Document(Map.of("_revision", 1L, "location", "Sofia")))
                            .append("online", 5)
                            .append("offline", 3)))
            ).when(mockPersistence)
                    .aggregateThings(any());

            // Create the actor
            Props props = AggregateThingsMetricsActor.props(mockPersistence);
            final var actorRef = system.actorOf(props);

            // Prepare the test message
            Map<String, String> groupingBy =
                    Map.of("_revision", "$_revision", "location", "$t.attributes.Info.location");
            Map<String, String> namedFilters = Map.of(
                    "online", "gt(features/ConnectionStatus/properties/status/readyUntil/,time:now)",
                    "offline", "lt(features/ConnectionStatus/properties/status/readyUntil/,time:now)");
            Set<String> namespaces = Collections.singleton("namespace");
            DittoHeaders headers = DittoHeaders.newBuilder().build();
            AggregateThingsMetrics metrics =
                    AggregateThingsMetrics.of("metricName", groupingBy, namedFilters, namespaces, headers);

            // Send the message to the actor
            actorRef.tell(metrics, getRef());

            final JsonObject mongoAggregationResult = JsonFactory.newObjectBuilder()
                    .set("_id", JsonFactory.newObjectBuilder()
                            .set("_revision", 1)
                            .set("location", "Berlin")
                            .build())
                    .set("online", 6)
                    .set("offline", 0)
                    .build();
            final AggregateThingsMetricsResponse
                    expectedResponse = AggregateThingsMetricsResponse.of(mongoAggregationResult, metrics);
            expectMsg(expectedResponse);

            // Verify interactions with the mock (this depends on your actual implementation)
            verify(mockPersistence, times(1)).aggregateThings(metrics);
        }};
    }

    @Test
    public void testUnknownMessage() {
        new TestKit(system) {{
            // Create a mock persistence object

            // Create the actor
            Props props = AggregateThingsMetricsActor.props(mock(ThingsAggregationPersistence.class));
            final var actorRef = system.actorOf(props);

            // Send an unknown message to the actor
            actorRef.tell("unknown message", getRef());

            // Verify that the actor does not crash and handles the unknown message gracefully
            expectNoMessage();
        }};
    }
}
