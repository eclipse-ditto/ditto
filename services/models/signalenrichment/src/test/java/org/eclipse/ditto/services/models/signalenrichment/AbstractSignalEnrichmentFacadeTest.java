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
package org.eclipse.ditto.services.models.signalenrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.DittoTestSystem;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Test;

import akka.pattern.AskTimeoutException;
import akka.testkit.javadsl.TestKit;

/**
 * Tests different {@link org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade} implementations.
 */
abstract class AbstractSignalEnrichmentFacadeTest {

    protected static final JsonFieldSelector SELECTOR =
            JsonFieldSelector.newInstance("policyId", "attributes/x", "features/y/properties/z");

    @Test
    public void success() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            kit.expectMsg(RetrieveThing.getBuilder(thingId, headers).withSelectedFields(actualSelectedFields(SELECTOR)).build());
            final JsonObject partialThing = getSuccessPartialThingJson();
            kit.reply(RetrieveThingResponse.of(thingId, partialThing, headers));

            // THEN: The result future completes with the entity of the RetrieveThingResponse
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(partialThing);
        });
    }

    protected JsonFieldSelector actualSelectedFields(final JsonFieldSelector selector) {
        return selector;
    }

    protected JsonObject getSuccessPartialThingJson() {
        return JsonObject.of("{\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"attributes\": {\"x\":  5},\n" +
                "  \"features\": {\"y\": {\"properties\": {\"z\":  true}}}\n" +
                "}");
    }

    @Test
    public void thingNotAccessible() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler receives expected RetrieveThing and responds with ThingNotAccessibleException
            kit.expectMsg(RetrieveThing.getBuilder(thingId, headers).withSelectedFields(actualSelectedFields(SELECTOR)).build());
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
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler receives expected RetrieveThing and responds with a random object
            kit.expectMsg(RetrieveThing.getBuilder(thingId, headers).withSelectedFields(actualSelectedFields(SELECTOR)).build());
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
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler does not respond
            kit.expectMsg(RetrieveThing.getBuilder(thingId, headers).withSelectedFields(actualSelectedFields(SELECTOR)).build());

            // THEN: The result future fails with an AskTimeoutException.
            askResult.toCompletableFuture().exceptionally(e -> null).join();
            assertThat(askResult).hasFailedWithThrowableThat()
                    .isInstanceOf(AskTimeoutException.class);
        });

    }

    protected abstract SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(TestKit kit, Duration duration);
}
