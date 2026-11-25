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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
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
    private static final String NESTED_FEATURE_PROPERTY = "status";
    private static final Instant EVENT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Mirrors a real ThingModel @context: the Ditto WoT extension bound to the "ditto" prefix,
    // so the publisher's getAtContext().determinePrefixFor(...) resolves it.
    private static final JsonArray DITTO_CONTEXT = JsonFactory.newArrayBuilder()
            .add("https://www.w3.org/2022/wot/td/v1.1")
            .add(JsonFactory.newObjectBuilder()
                    .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                    .build())
            .build();

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
    public void tagPlaceholdersAreResolvedAgainstThing() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            // "building" is a placeholder into the Thing's attributes; "env" is a constant.
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of(
                    "building", "{{ thing-json:attributes/building }}",
                    "env", "prod")));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinitionAndAttributes()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints().get(0).getTags())
                    .containsEntry("building", JsonValue.of("A"))
                    .containsEntry("env", JsonValue.of("prod"));
        }};
    }

    @Test
    public void singleThingJsonPlaceholderTagKeepsTheFieldsJsonType() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of(
                    "floor", "{{ thing-json:attributes/floor }}",
                    "label", "floor-{{ thing-json:attributes/floor }}",
                    "env", "2")));
            final Thing thing = thingWithDefinitionAndAttributes()
                    .setAttribute(JsonPointer.of("floor"), JsonValue.of(2));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thing), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints().get(0).getTags())
                    .containsEntry("floor", JsonValue.of(2))
                    .containsEntry("label", JsonValue.of("floor-2"))
                    .containsEntry("env", JsonValue.of("2"));
        }};
    }

    @Test
    public void tagsResolveOnlyRegularThingFields() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of(
                    "building", "{{ thing-json:attributes/building }}",
                    "revision", "{{ thing-json:_revision }}",
                    "source", "{{ thing-json:_metadata/attributes/building/source }}")));
            final Thing thing = thingWithDefinitionAndAttributes().toBuilder()
                    .setRevision(7L)
                    .setMetadata(ThingsModelFactory.newMetadataBuilder()
                            .set(JsonPointer.of("attributes/building/source"), JsonValue.of("import"))
                            .build())
                    .build();

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thing), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints().get(0).getTags())
                    .containsOnlyKeys("building")
                    .containsEntry("building", JsonValue.of("A"));
        }};
    }

    @Test
    public void unresolvableTagPlaceholderIsDroppedNotStoredEmpty() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            // "floor" points to an attribute the Thing does not have -> that tag is dropped for the
            // point (not stored empty, not failing ingestion); the constant "env" survives.
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of(
                    "floor", "{{ thing-json:attributes/floor }}",
                    "env", "prod")));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinitionAndAttributes()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints().get(0).getTags())
                    .containsEntry("env", JsonValue.of("prod"))
                    .doesNotContainKey("floor");
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

    @Test
    public void retryIsDelayedByBackoff() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(annotatedSubmodel("ALL", Map.of()));

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(
                    sampleEvent(JsonValue.of(21.5)), thingWithDefinition()), getRef());

            shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(new Status.Failure(new RuntimeException("backend hiccup")));

            shardProbe.expectNoMessage(scala.concurrent.duration.Duration.create(
                    TimeseriesIngestPublisher.RETRY_BACKOFF.toMillis() - 100, TimeUnit.MILLISECONDS));
            final IngestDataPoints second = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, second.getDittoHeaders()));
        }};
    }

    @Test
    public void nestedScalarAnnotationIngestsOnlyAnnotatedLeaf() {
        // The object property `status` is set in one go to {temperature, updatedAt}; only
        // the nested `temperature` carries the annotation, so `updatedAt` must be skipped.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(nestedAnnotatedSubmodel());

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            final FeaturePropertyModified event = FeaturePropertyModified.of(THING_ID, FEATURE_ID,
                    JsonPointer.of(NESTED_FEATURE_PROPERTY), statusValue(), 1L,
                    Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thingWithNestedStatus()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints()).hasSize(1);
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/env/properties/status/temperature"));
            assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(55.0));
        }};
    }

    @Test
    public void annotationWithoutIngestSkipsOnlyThatPropertyAndDoesNotFailThePublisher() {
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(submodelWithAnnotationWithoutIngest());
            final ActorRef publisher = childActorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)),
                    SupervisorStrategy.stoppingStrategy());
            watch(publisher);

            final ThingMerged event = ThingMerged.of(THING_ID, JsonPointer.of("/features/env/properties"),
                    JsonObject.newBuilder().set(PROPERTY_NAME, 21.5).set("humidity", 40).build(), 1L,
                    Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thingWithDefinition()), getRef());
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thingWithDefinition()), getRef());

            for (int i = 0; i < 2; i++) {
                final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
                shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));
                assertThat(command.getDataPoints()).hasSize(1);
                assertThat((Object) command.getDataPoints().get(0).getPath())
                        .isEqualTo(JsonPointer.of("/features/env/properties/temperature"));
            }
            expectNoMessage(scala.concurrent.duration.Duration.create(300, TimeUnit.MILLISECONDS));
        }};
    }

    @Test
    public void mergeEventProducesIngestDataPoints() {
        // A merge command targeting features/env/properties/status produces a ThingMerged;
        // the publisher must decompose the merged object and ingest the annotated leaf.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(nestedAnnotatedSubmodel());

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            final ThingMerged event = ThingMerged.of(THING_ID,
                    JsonPointer.of("/features/env/properties/status"), statusValue(), 1L,
                    Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thingWithNestedStatus()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints()).hasSize(1);
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/env/properties/status/temperature"));
            assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(55.0));
        }};
    }

    @Test
    public void thingModifiedReplaceProducesIngestDataPoints() {
        // A full-Thing replace fires ThingModified; the publisher must walk every feature's
        // properties and ingest the annotated leaf.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(nestedAnnotatedSubmodel());

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            final Thing thing = thingWithNestedStatus();
            final ThingModified event = ThingModified.of(thing, 1L,
                    Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thing), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints()).hasSize(1);
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/env/properties/status/temperature"));
            assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(55.0));
        }};
    }

    @Test
    public void categorisedNestedAnnotationResolvesUnderCategorySegment() {
        // flowTemperature declares ditto:category "status", so its data lives under
        // status/flowTemperature/...; the annotated nested temperature must still resolve.
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final WotThingModelResolver resolver = resolverWith(categorisedAnnotatedSubmodel());

            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolver, Duration.ofSeconds(2)));

            final FeaturePropertyModified event = FeaturePropertyModified.of(THING_ID, FEATURE_ID,
                    JsonPointer.of("status/flowTemperature"), statusValue(), 1L,
                    Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);
            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thingWithNestedStatus()), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));

            assertThat(command.getDataPoints()).hasSize(1);
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/env/properties/status/flowTemperature/temperature"));
            assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(55.0));
        }};
    }

    // --- WoT TM construction helpers ---

    @Test
    public void dataPointWithoutDeclaredTimeTakesTheEventTime() {
        final IngestDataPoints command = ingest(nestedAnnotatedSubmodel(),
                mergeStatus(status(55.0, "2025-12-31T23:00:00Z")));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void declaredTimeFieldWrittenByTheEventIsTheMeasurementTime() {
        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(status(55.0, "2025-12-31T23:00:00Z")));

        assertThat(command.getDataPoints()).hasSize(1);
        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
    }

    @Test
    public void declaredTimeWithAnOffsetIsReadAsTheSameInstant() {
        final IngestDataPoints command =
                ingest(timedSubmodel(), mergeStatus(status(55.0, "2025-12-31T23:00:00+01:00")));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(Instant.parse("2025-12-31T22:00:00Z"));
    }

    @Test
    public void declaredTimeIsReadFromAFullThingReplace() {
        final Thing thing = thingWithStatus(status(55.0, "2025-12-31T23:00:00Z"));
        final IngestDataPoints command = ingest(timedSubmodel(),
                ThingModified.of(thing, 1L, EVENT_TIME, DittoHeaders.empty(), null), thing);

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
    }

    @Test
    public void valueUpdatedWithoutItsTimeFieldTakesTheEventTime() {
        // The Thing still holds the time of the previous measurement; it must not be reused for the new value.
        final Thing thing = thingWithStatus(status(56.0, "2025-12-31T23:00:00Z"));
        final FeaturePropertyModified event = FeaturePropertyModified.of(THING_ID, FEATURE_ID,
                JsonPointer.of("status/temperature"), JsonValue.of(56.0), 2L, EVENT_TIME, DittoHeaders.empty(), null);

        final IngestDataPoints command = ingest(timedSubmodel(), event, thing);

        assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(56.0));
        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void missingTimeFieldTakesTheEventTime() {
        final JsonObject withoutTime = JsonFactory.newObjectBuilder().set("temperature", 55.0).build();

        final IngestDataPoints command =
                ingest(timedSubmodel(), mergeStatus(withoutTime), thingWithStatus(withoutTime));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void timeFieldThatIsNoInstantTakesTheEventTime() {
        for (final JsonValue time : new JsonValue[] {
                JsonValue.of("yesterday"), JsonValue.of("2025-12-31"), JsonValue.of(1767222000000L)}) {
            final JsonObject value =
                    JsonFactory.newObjectBuilder().set("temperature", 55.0).set("updatedAt", time).build();

            final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(value), thingWithStatus(value));

            assertThat(command.getDataPoints().get(0).getTimestamp()).as("time %s", time).isEqualTo(EVENT_TIME);
        }
    }

    @Test
    public void timeLaterThanTheEventIsCappedAtTheEventTime() {
        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(status(55.0, "2026-01-01T00:00:01Z")));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void declaredTimeIsReadFromEveryEventShapeThatWritesIt() {
        final JsonObject status = status(55.0, "2025-12-31T23:00:00Z");
        final Thing thing = thingWithStatus(status);
        final Feature feature = thing.getFeatures().flatMap(f -> f.getFeature(FEATURE_ID)).orElseThrow();
        final List<ThingEvent<?>> events = List.of(
                FeaturePropertiesModified.of(THING_ID, FEATURE_ID, feature.getProperties().orElseThrow(), 1L,
                        EVENT_TIME, DittoHeaders.empty(), null),
                FeatureModified.of(THING_ID, feature, 1L, EVENT_TIME, DittoHeaders.empty(), null),
                FeaturesModified.of(THING_ID, thing.getFeatures().orElseThrow(), 1L, EVENT_TIME, DittoHeaders.empty(),
                        null),
                ThingMerged.of(THING_ID, JsonPointer.of("/features"), thing.getFeatures().orElseThrow().toJson(), 1L,
                        EVENT_TIME, DittoHeaders.empty(), null),
                ThingMerged.of(THING_ID, JsonPointer.empty(), thing.toJson(), 1L, EVENT_TIME, DittoHeaders.empty(),
                        null));

        for (final ThingEvent<?> event : events) {
            final IngestDataPoints command = ingest(timedSubmodel(), event, thing);

            assertThat(command.getDataPoints()).as(event.getType()).hasSize(1);
            assertThat(command.getDataPoints().get(0).getTimestamp()).as(event.getType())
                    .isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
        }
    }

    @Test
    public void timeFieldRemovedByMergePatchTakesTheEventTime() {
        final JsonObject patch = JsonFactory.newObjectBuilder()
                .set("temperature", 55.0)
                .set("updatedAt", JsonValue.nullLiteral())
                .build();
        final Thing thing = thingWithStatus(JsonFactory.newObjectBuilder().set("temperature", 55.0).build());

        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(patch), thing);

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void timeEqualToTheEventTimeIsKept() {
        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(status(55.0, EVENT_TIME.toString())));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(EVENT_TIME);
    }

    @Test
    public void invalidTimeAnnotationSkipsOnlyItsProperty() {
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set(PROPERTY_NAME, JsonFactory.newObjectBuilder()
                        .set("type", "number")
                        .set("ditto:timeseries", JsonObject.newBuilder().set("ingest", "ALL").build())
                        .build())
                .set("humidity", JsonFactory.newObjectBuilder()
                        .set("type", "number")
                        .set("ditto:timeseries", JsonObject.newBuilder()
                                .set("ingest", "ALL")
                                .set("time", "{{ thing-json:features/env/properties/updatedAt }}")
                                .build())
                        .build())
                .build();
        final ThingModel submodel = ThingModel.fromJson(JsonObject.newBuilder()
                .set("@context", DITTO_CONTEXT)
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
        final ThingMerged event = ThingMerged.of(THING_ID, JsonPointer.of("/features/env/properties"),
                JsonObject.newBuilder().set(PROPERTY_NAME, 21.5).set("humidity", 40).build(), 1L, EVENT_TIME,
                DittoHeaders.empty(), null);

        final IngestDataPoints command = ingest(submodel, event, thingWithDefinition());

        assertThat(command.getDataPoints()).hasSize(1);
        assertThat((Object) command.getDataPoints().get(0).getPath())
                .isEqualTo(JsonPointer.of("/features/env/properties/temperature"));
    }

    @Test
    public void resentValueAndTimeGiveNoDataPoint() {
        final JsonObject status = status(55.0, "2025-12-31T23:00:00Z");
        final Thing thing = thingWithStatus(status);

        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolverWith(timedSubmodel()),
                            Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(mergeStatus(status), thing, thing), getRef());

            shardProbe.expectNoMessage(scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS));
        }};
    }

    @Test
    public void correctedValueWithTheSameTimeIsKept() {
        final Thing previous = thingWithStatus(status(55.0, "2025-12-31T23:00:00Z"));
        final JsonObject corrected = status(54.0, "2025-12-31T23:00:00Z");

        final IngestDataPoints command =
                ingest(timedSubmodel(), mergeStatus(corrected), thingWithStatus(corrected), previous);

        assertThat(command.getDataPoints().get(0).getValue()).isEqualTo(JsonValue.of(54.0));
        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
    }

    @Test
    public void sameValueWithANewTimeIsKept() {
        final Thing previous = thingWithStatus(status(55.0, "2025-12-31T22:00:00Z"));
        final JsonObject next = status(55.0, "2025-12-31T23:00:00Z");

        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(next), thingWithStatus(next), previous);

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
    }

    @Test
    public void timeOlderThanTheMaximumMeasurementAgeTakesTheEventTime() {
        final String[] times = {"1970-01-01T00:00:05Z", EVENT_TIME.minus(Duration.ofDays(36)).toString()};
        for (final String time : times) {
            final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(status(55.0, time)));

            assertThat(command.getDataPoints().get(0).getTimestamp()).as(time).isEqualTo(EVENT_TIME);
        }
    }

    @Test
    public void timeWithinTheMaximumMeasurementAgeIsKept() {
        final Instant time = EVENT_TIME.minus(Duration.ofDays(34));

        final IngestDataPoints command = ingest(timedSubmodel(), mergeStatus(status(55.0, time.toString())));

        assertThat(command.getDataPoints().get(0).getTimestamp()).isEqualTo(time);
    }

    @Test
    public void timePathIsResolvedInTheFeatureOfTheValue() {
        // the same submodel mounted under another feature name reads the time from its own feature
        final JsonObject status = status(55.0, "2025-12-31T23:00:00Z");
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setDefinition(ThingsModelFactory.newDefinition(TM_IRI.toString()))
                .setFeature(ThingsModelFactory.newFeatureBuilder()
                        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                .set(NESTED_FEATURE_PROPERTY, status)
                                .build())
                        .withId("system")
                        .build())
                .build();
        final ThingMerged event = ThingMerged.of(THING_ID, JsonPointer.of("/features/system/properties/status"),
                status, 1L, EVENT_TIME, DittoHeaders.empty(), null);

        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final ActorRef publisher = actorSystem.actorOf(TimeseriesIngestPublisher.props(shardProbe.ref(),
                    resolverWith("system", timedSubmodel()), Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thing), getRef());

            final IngestDataPoints command = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, command.getDittoHeaders()));
            assertThat((Object) command.getDataPoints().get(0).getPath())
                    .isEqualTo(JsonPointer.of("/features/system/properties/status/temperature"));
            assertThat(command.getDataPoints().get(0).getTimestamp())
                    .isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
        }};
    }

    private static IngestDataPoints ingest(final ThingModel submodel, final ThingMerged event) {
        return ingest(submodel, event, thingWithStatus(event.getValue().asObject()));
    }

    private static IngestDataPoints ingest(final ThingModel submodel, final ThingEvent<?> event, final Thing thing) {
        return ingest(submodel, event, thing, null);
    }

    private static IngestDataPoints ingest(final ThingModel submodel, final ThingEvent<?> event, final Thing thing,
            @Nullable final Thing previousThing) {

        final IngestDataPoints[] result = new IngestDataPoints[1];
        new TestKit(actorSystem) {{
            final TestProbe shardProbe = new TestProbe(actorSystem);
            final ActorRef publisher = actorSystem.actorOf(
                    TimeseriesIngestPublisher.props(shardProbe.ref(), resolverWith(submodel), Duration.ofSeconds(2)));

            publisher.tell(new TimeseriesIngestPublisher.IngestRequest(event, thing, previousThing), getRef());

            result[0] = shardProbe.expectMsgClass(IngestDataPoints.class);
            shardProbe.reply(IngestDataPointsResponse.of(THING_ID, result[0].getDittoHeaders()));
            actorSystem.stop(publisher);
        }};
        return result[0];
    }

    private static ThingMerged mergeStatus(final JsonObject status) {
        return ThingMerged.of(THING_ID, JsonPointer.of("/features/env/properties/status"), status, 1L, EVENT_TIME,
                DittoHeaders.empty(), null);
    }

    private static JsonObject status(final double temperature, final String updatedAt) {
        return JsonFactory.newObjectBuilder().set("temperature", temperature).set("updatedAt", updatedAt).build();
    }

    private static Thing thingWithStatus(final JsonObject status) {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setDefinition(ThingsModelFactory.newDefinition(TM_IRI.toString()))
                .setFeature(ThingsModelFactory.newFeatureBuilder()
                        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                .set(NESTED_FEATURE_PROPERTY, status)
                                .build())
                        .withId(FEATURE_ID)
                        .build())
                .build();
    }

    private static ThingModel timedSubmodel() {
        final JsonObject annotation = JsonFactory.newObjectBuilder()
                .set("ingest", "ALL")
                .set("time", "status/updatedAt")
                .build();
        final JsonObject nestedProperties = JsonFactory.newObjectBuilder()
                .set("temperature", JsonFactory.newObjectBuilder()
                        .set("type", "number")
                        .set("ditto:timeseries", annotation)
                        .build())
                .set("updatedAt", JsonFactory.newObjectBuilder()
                        .set("type", "string")
                        .set("format", "date-time")
                        .build())
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@context", DITTO_CONTEXT)
                .set("@type", "tm:ThingModel")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set(NESTED_FEATURE_PROPERTY, JsonFactory.newObjectBuilder()
                                .set("type", "object")
                                .set("properties", nestedProperties)
                                .build())
                        .build())
                .build());
    }

    private static WotThingModelResolver resolverWith(final ThingModel submodel) {
        return resolverWith(FEATURE_ID, submodel);
    }

    private static WotThingModelResolver resolverWith(final String featureId, final ThingModel submodel) {
        final WotThingModelResolver resolver = mock(WotThingModelResolver.class);
        final ThingModel topLevel = ThingModel.fromJson(JsonObject.empty());
        when(resolver.resolveThingModel(any(IRI.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(topLevel));
        final Map<ThingSubmodel, ThingModel> submodels = new HashMap<>();
        submodels.put(new ThingSubmodel(featureId, IRI.of("https://example.com/env.tm.jsonld")),
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
                .set("@context", DITTO_CONTEXT)
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
    }

    private static ThingModel nestedAnnotatedSubmodel() {
        // An object property `status` with an annotated nested `temperature` scalar and an
        // un-annotated `updatedAt` sibling — a typical temperature submodel.
        final JsonObject annotation = JsonFactory.newObjectBuilder()
                .set("ingest", "ALL")
                .build();
        final JsonObject temperatureSchema = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .set("unit", "Cel")
                .set("ditto:timeseries", annotation)
                .build();
        final JsonObject updatedAtSchema = JsonFactory.newObjectBuilder()
                .set("type", "string")
                .set("format", "date-time")
                .build();
        final JsonObject nestedProperties = JsonFactory.newObjectBuilder()
                .set("temperature", temperatureSchema)
                .set("updatedAt", updatedAtSchema)
                .build();
        final JsonObject statusSchema = JsonFactory.newObjectBuilder()
                .set("type", "object")
                .set("properties", nestedProperties)
                .build();
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set(NESTED_FEATURE_PROPERTY, statusSchema)
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@context", DITTO_CONTEXT)
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
    }

    private static ThingModel categorisedAnnotatedSubmodel() {
        // flowTemperature is an object property grouped under ditto:category "status", with an
        // annotated nested temperature scalar — a typical heating circuit submodel.
        final JsonObject annotation = JsonFactory.newObjectBuilder()
                .set("ingest", "ALL")
                .build();
        final JsonObject temperatureSchema = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .set("unit", "Cel")
                .set("ditto:timeseries", annotation)
                .build();
        final JsonObject updatedAtSchema = JsonFactory.newObjectBuilder()
                .set("type", "string")
                .set("format", "date-time")
                .build();
        final JsonObject nestedProperties = JsonFactory.newObjectBuilder()
                .set("temperature", temperatureSchema)
                .set("updatedAt", updatedAtSchema)
                .build();
        final JsonObject flowTemperatureSchema = JsonFactory.newObjectBuilder()
                .set("type", "object")
                .set("ditto:category", "status")
                .set("properties", nestedProperties)
                .build();
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set("flowTemperature", flowTemperatureSchema)
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@context", DITTO_CONTEXT)
                .set("@type", "tm:ThingModel")
                .set("properties", properties)
                .build());
    }

    private static JsonObject statusValue() {
        return JsonFactory.newObjectBuilder()
                .set("temperature", JsonValue.of(55.0))
                .set("updatedAt", JsonValue.of("2026-01-01T00:00:00Z"))
                .build();
    }

    private static Thing thingWithNestedStatus() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setDefinition(ThingsModelFactory.newDefinition(TM_IRI.toString()))
                .setFeature(ThingsModelFactory.newFeatureBuilder()
                        .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                                .set(NESTED_FEATURE_PROPERTY, statusValue())
                                .build())
                        .withId(FEATURE_ID)
                        .build())
                .build();
    }

    private static ThingModel submodelWithAnnotationWithoutIngest() {
        final JsonObject properties = JsonFactory.newObjectBuilder()
                .set(PROPERTY_NAME, JsonFactory.newObjectBuilder()
                        .set("type", "number")
                        .set("ditto:timeseries", JsonObject.newBuilder().set("ingest", "ALL").build())
                        .build())
                .set("humidity", JsonFactory.newObjectBuilder()
                        .set("type", "number")
                        .set("ditto:timeseries", JsonObject.newBuilder().set("tags", JsonObject.empty()).build())
                        .build())
                .build();
        return ThingModel.fromJson(JsonObject.newBuilder()
                .set("@context", DITTO_CONTEXT)
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
                .set("@context", DITTO_CONTEXT)
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

    private static Thing thingWithDefinitionAndAttributes() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setDefinition(ThingsModelFactory.newDefinition(TM_IRI.toString()))
                .setAttribute(JsonPointer.of("building"), JsonValue.of("A"))
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
