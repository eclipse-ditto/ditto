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
package org.eclipse.ditto.internal.models.signalenrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.DittoTestSystem;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;
import org.eclipse.ditto.internal.utils.tracing.filter.AcceptAllTracingFilter;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Abstract base test for different {@link SignalEnrichmentFacade} implementations.
 */
abstract class AbstractSignalEnrichmentFacadeTest {

    protected static final JsonFieldSelector SELECTOR =
            JsonFieldSelector.newInstance("policyId", "attributes/x", "features/y/properties/z", "_metadata");

    protected static final String RESULT_POLICY_ID = "policy:id";
    private static final AttributeModified THING_EVENT = AttributeModified.of(ThingId.generateRandom(),
            JsonPointer.of("x"),
            JsonValue.of(5),
            3L,
            Instant.EPOCH,
            DittoHeaders.empty(),
            MetadataModelFactory.newMetadataBuilder()
                    .set("type", "x attribute")
                    .build());

    protected static final Config CONFIG = ConfigFactory.load("signal-enrichment-facade-test");

    @BeforeClass
    public static void beforeClass() {
        final TracingConfig tracingConfigMock = Mockito.mock(TracingConfig.class);
        Mockito.when(tracingConfigMock.isTracingEnabled()).thenReturn(true);
        Mockito.when(tracingConfigMock.getPropagationChannel()).thenReturn("default");
        Mockito.when(tracingConfigMock.getTracingFilter()).thenReturn(AcceptAllTracingFilter.getInstance());
        DittoTracing.init(tracingConfigMock);
    }

    @AfterClass
    public static void afterClass() {
        DittoTracing.reset();
    }

    @Test
    public void success() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.of("test:thing-id");
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));

            // THEN: The result future completes with the entity of the RetrieveThingResponse
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());
        });
    }

    protected JsonFieldSelector actualSelectedFields(final JsonFieldSelector selector) {
        return selector;
    }

    protected JsonObject getThingResponseThingJson() {
        return JsonObject.of("{\n" +
                "  \"policyId\": \"" + RESULT_POLICY_ID + "\",\n" +
                "  \"attributes\": {\"x\":  5},\n" +
                "  \"features\": {\"y\": {\"properties\": {\"z\":  true}}}\n" +
                "}");
    }

    protected JsonObject getExpectedThingJson() {
        return getThingResponseThingJson();
    }

    protected JsonFieldSelector getJsonFieldSelector() {
        return SELECTOR;
    }

    protected AttributeModified getThingEvent() {
        return THING_EVENT;
    }

    @Test
    public void thingNotAccessible() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with ThingNotAccessibleException
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));
            final ThingNotAccessibleException thingNotAccessibleException =
                    ThingNotAccessibleException.newBuilder(thingId).dittoHeaders(headers).build();
            kit.reply(thingNotAccessibleException);

            // THEN: The result future fails with ThingNotAccessibleException
            askResult.toCompletableFuture().exceptionally(e -> null).join();
            assertThat(askResult).hasFailedWithThrowableThat().isEqualTo(thingNotAccessibleException);
        });
    }

    @Test
    public void unexpectedMessage() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with a random object
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));
            final Object randomObject = new Object();
            kit.reply(randomObject);

            // THEN: The result future fails with a runtime exception containing a description of the random object
            askResult.toCompletableFuture().exceptionally(e -> null).join();
            assertThat(askResult).hasFailedWithThrowableThat()
                    .hasMessageContaining(randomObject.toString())
                    .isInstanceOf(RuntimeException.class);
        });
    }

    @Test
    public void timeout() {
        DittoTestSystem.run(this, ConfigFactory.load("signal-enrichment-facade-test-short-timeout"), kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing() with a short timeout
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofMillis(1L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, THING_EVENT);

            // WHEN: Command handler does not respond
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));

            // THEN: The result future fails with an AskTimeoutException.
            askResult.toCompletableFuture().exceptionally(e -> null).join();
            assertThat(askResult).hasFailedWithThrowableThat()
                    .isInstanceOf(AskException.class);
        });
    }

    @Test
    public void enrichThingDeleted() {
        DittoTestSystem.run(this, CONFIG, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final ThingDeleted thingDeleted = ThingDeleted.of(thingId, 2L, Instant.EPOCH, headers, null);

            // WHEN: ThingDeleted event is about to be enriched by facade
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, thingDeleted);
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));

            // THEN: The result future completes with the entity of the RetrieveThingResponse
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());
        });
    }

    @Test
    public void enrichThingDefinitionMigrated() {
        DittoTestSystem.run(this, CONFIG, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final Thing thing = Thing.newBuilder().setId(thingId)
                    .setDefinition(ThingsModelFactory.newDefinition("foo:bar:baz"))
                    .setFeatureDefinition("y", FeatureDefinition.fromIdentifier("some:feature:foo"))
                    .build();
            final ThingDefinitionMigrated thingDefinitionMigrated = ThingDefinitionMigrated.of(thing, 4L, Instant.EPOCH, headers, null);

            // WHEN: ThingDefinitionMigrated event is about to be enriched by facade
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, getJsonFieldSelector(), headers, thingDefinitionMigrated);
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(getJsonFieldSelector()));
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));

            // THEN: The result future completes with the entity of the RetrieveThingResponse
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());
        });
    }

    protected abstract SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(TestKit kit, Duration duration);

}
