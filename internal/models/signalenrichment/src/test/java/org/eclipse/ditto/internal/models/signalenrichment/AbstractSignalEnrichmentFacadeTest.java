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

import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.DittoTestSystem;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.Test;

import akka.pattern.AskTimeoutException;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract base test for different {@link SignalEnrichmentFacade} implementations.
 */
abstract class AbstractSignalEnrichmentFacadeTest {

    protected static final JsonFieldSelector SELECTOR =
            JsonFieldSelector.newInstance("policyId", "attributes/x", "features/y/properties/z", "_metadata");

    protected static final String RESULT_POLICY_ID = "policy:id";
    protected static final AttributeModified THING_EVENT = AttributeModified.of(ThingId.generateRandom(),
            JsonPointer.of("x"),
            JsonValue.of(5),
            3L,
            Instant.EPOCH,
            DittoHeaders.empty(),
            MetadataModelFactory.newMetadataBuilder()
                    .set("type", "x attribute")
                    .build());

    @Test
    public void success() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.of("test:thing-id");
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
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

    @Test
    public void thingNotAccessible() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with ThingNotAccessibleException
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
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
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with a random object
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
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
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing() with a short timeout
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofMillis(1L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler does not respond
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));

            // THEN: The result future fails with an AskTimeoutException.
            askResult.toCompletableFuture().exceptionally(e -> null).join();
            assertThat(askResult).hasFailedWithThrowableThat()
                    .isInstanceOf(AskTimeoutException.class);
        });
    }

    @Test
    public void enrichThingDeleted() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final ThingDeleted thingDeleted = ThingDeleted.of(thingId, 2L, Instant.EPOCH, headers, null);

            // WHEN: ThingDeleted event is about to be enriched by facade
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, thingDeleted);
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));

            // THEN: The result future completes with the entity of the RetrieveThingResponse
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());
        });
    }

    protected abstract SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(TestKit kit, Duration duration);
}
