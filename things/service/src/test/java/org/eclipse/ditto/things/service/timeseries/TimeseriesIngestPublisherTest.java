/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.timeseries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link TimeseriesIngestPublisher}. The publisher is the
 * non-persistent half of the ingest pipeline; its contract is "given an event +
 * post-event Thing entity + a WoT TM with {@code ditto:timeseries} annotations, ask
 * the timeseries shard region with the right {@link IngestDataPoints}, retry on
 * missing acks, give up after {@code MAX_ATTEMPTS}." The persistent shard entity is
 * tested separately in {@code TimeseriesIngestActorTest}.
 */
public final class TimeseriesIngestPublisherTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final IRI TM_IRI = IRI.of("https://example.com/sensor-1.tm.jsonld");
    private static final String FEATURE_ID = "env";
    private static final String PROPERTY_NAME = "temperature";

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("TimeseriesIngestPublisherTest", ConfigFactory.empty());
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void propertyWithIngestAllAnnotationProducesIngestDataPoints() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of()));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            // Ack so the publisher does not retry — not under test in this case.
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat((Object) command.getEntityId()).isEqualTo(THING_ID);
            assertThat(command.getDataPoints()).hasSize(1);
            assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(21.5));
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/env/properties/temperature"));
        }};
    }

    @Test
    public void propertyWithIngestNoneAnnotationDoesNotPublish() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("NONE", Map.of()));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            shardProbe.expectNoMessage(scala.concurrent.duration.Duration.create(300, TimeUnit.MILLISECONDS));
        }};
    }

    @Test
    public void propertyWithoutAnnotationDoesNotPublish() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(submodelWithoutAnnotation());

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            shardProbe.expectNoMessage(scala.concurrent.duration.Duration.create(300, TimeUnit.MILLISECONDS));
        }};
    }

    @Test
    public void thingWithoutDefinitionDoesNotPublish() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            // Strict resolver that fails if asked — proves the publisher never calls it.
            final WotThingModelResolver resolver = mock(WotThingModelResolver.class);
            when(resolver.resolveThingModel(any(IRI.class), any(DittoHeaders.class)))
                    .thenThrow(new AssertionError("resolveThingModel should not be called for a definition-less Thing"));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithoutDefinition()), getRef());

            shardProbe.expectNoMessage(scala.concurrent.duration.Duration.create(300, TimeUnit.MILLISECONDS));
        }};
    }

    @Test
    public void retriesOnNoAck() {
        // Critical contract: when the shard region doesn't reply within the ask
        // timeout, the publisher retries with the same correlation-id so the
        // persistent entity recognises the replay.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of()));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofMillis(150)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            final IngestDataPoints first = shardProbe.expectMsgClass(IngestDataPoints.class);

            final IngestDataPoints second = shardProbe.expectMsgClass(
                    scala.concurrent.duration.Duration.create(2, TimeUnit.SECONDS),
                    IngestDataPoints.class);
            assertThat(second.getDittoHeaders().getCorrelationId())
                    .isEqualTo(first.getDittoHeaders().getCorrelationId());

            // Ack the second to terminate retries cleanly — leaving it would result in
            // a third attempt and then a WARN log, which isn't under test here.
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, second.getDittoHeaders()));
        }};
    }

    @Test
    public void retriesOnStatusFailureFromShard() {
        // Status.Failure from the entity (e.g. transient MongoDB error) is treated
        // identically to a timeout — retry. Either condition could be transient and
        // benefit from a re-send.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of()));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            final IngestDataPoints first = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(new Status.Failure(new RuntimeException("backend hiccup")));

            final IngestDataPoints second = shardProbe.expectMsgClass(IngestDataPoints.class);
            assertThat(second.getDittoHeaders().getCorrelationId())
                    .isEqualTo(first.getDittoHeaders().getCorrelationId());

            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, second.getDittoHeaders()));
        }};
    }

    // --- WoT TM construction helpers ---

    private static WotThingModelResolver resolverWith(final ThingModel submodel) {
        final WotThingModelResolver resolver = mock(WotThingModelResolver.class);
        final ThingModel topLevel = ThingModel.fromJson(JsonObject.empty());
        when(resolver.resolveThingModel(any(IRI.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(topLevel));
        final Map<ThingSubmodel, ThingModel> submodels = new HashMap<>();
        submodels.put(new ThingSubmodel(FEATURE_ID, IRI.of("https://example.com/env.tm.jsonld")),
                submodel);
        when(resolver.resolveThingModelSubmodels(any(ThingModel.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(submodels));
        return resolver;
    }

    private static ThingModel annotatedSubmodel(final String ingestMode, final Map<String, String> tags) {
        final JsonObject tagsJson = tags.entrySet().stream()
                .reduce(JsonFactory.newObjectBuilder(),
                        (b, e) -> b.set(e.getKey(), e.getValue()),
                        (a, b) -> a)
                .build();
        final JsonObject annotation = JsonFactory.newObjectBuilder()
                .set("ingest", ingestMode)
                .set("tags", tagsJson)
                .build();
        final JsonObject propertyJson = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .set("unit", "Cel")
                .set("ditto:timeseries", annotation)
                .build();
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set(PROPERTY_NAME, propertyJson)
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
    }

    private static ThingModel submodelWithoutAnnotation() {
        final JsonObject propertyJson = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .build();
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set(PROPERTY_NAME, propertyJson)
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
    }

    private static FeaturePropertyModified sampleEvent(final JsonValue value) {
        return FeaturePropertyModified.of(THING_ID, FEATURE_ID, JsonPointer.of(PROPERTY_NAME),
                value, 1L, Instant.parse("2026-01-01T00:00:00Z"),
                DittoHeaders.empty(), null);
    }

    private static Thing thingWithDefinition() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setDefinition(ThingsModelFactory.newDefinition(TM_IRI.toString()))
                .setFeature(ThingsModelFactory.newFeatureBuilder()
                        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                .set(PROPERTY_NAME, JsonValue.of(21.5))
                                .build())
                        .withId(FEATURE_ID)
                        .build())
                .build();
    }

    private static Thing thingWithoutDefinition() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setFeature(ThingsModelFactory.newFeatureBuilder()
                        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                .set(PROPERTY_NAME, JsonValue.of(21.5))
                                .build())
                        .withId(FEATURE_ID)
                        .build())
                .build();
    }
}
