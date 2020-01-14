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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.signals.base.DittoTestSystem;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link CachingSignalEnrichmentFacade}.
 */
public final class CachingSignalEnrichmentFacadeTest extends AbstractSignalEnrichmentFacadeTest {

    public static final String CACHE_CONFIG_KEY = "my-cache";
    public static final String CACHE_CONFIG = CACHE_CONFIG_KEY + " {\n" +
            "  maximum-size = 10\n" +
            "  expire-after-write = 2m\n" +
            "  expire-after-access = 1m\n" +
            "}";

    @Override
    protected SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(final TestKit kit,
            final Duration duration) {
        final CacheConfig cacheConfig = DefaultCacheConfig.of(ConfigFactory.parseString(CACHE_CONFIG), CACHE_CONFIG_KEY);
        return CachingSignalEnrichmentFacade.of(kit.getRef(), Duration.ofSeconds(10L), cacheConfig,
                        kit.getSystem().getDispatcher());
    }

    @Override
    protected JsonObject getThingResponseThingJson() {
        return JsonObject.of("{\n" +
                "  \"_revision\": 3,\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"attributes\": {\"x\":  5},\n" +
                "  \"features\": {\"y\": {\"properties\": {\"z\":  true}}}\n" +
                "}");
    }

    @Override
    protected JsonObject getExpectedThingJson() {
        return JsonObject.of("{\n" +
                "  \"policyId\": \"policy:id\",\n" +
                "  \"attributes\": {\"x\":  5},\n" +
                "  \"features\": {\"y\": {\"properties\": {\"z\":  true}}}\n" +
                "}");
    }

    @Override
    protected JsonFieldSelector actualSelectedFields(final JsonFieldSelector selector) {
        return JsonFactory.newFieldSelectorBuilder()
                .addPointers(selector)
                .addFieldDefinition(Thing.JsonFields.POLICY_ID) // additionally always select the policyId
                .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                .build();
    }

    @Test
    public void alreadyLoadedCacheEntryIsReused() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers,
                            THING_EVENT.setRevision(THING_EVENT.getRevision() + 1));

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            assertThat(askResultCached).isCompletedWithValue(getExpectedThingJson());
        });
    }

    @Test
    public void alreadyLoadedCacheEntryIsInvalidatedForUnexpectedEventRevision() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector with event with 2 revisions ahead
            final DittoHeaders headers2 = DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers2,
                            THING_EVENT.setRevision(THING_EVENT.getRevision() + 2)); // notice +2 here

            // THEN: do another cache lookup after invalidation
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            final Thing thing2 = ThingsModelFactory.newThing(getThingResponseThingJson());
            final Thing thing2WithUpdatedRev = thing2.toBuilder()
                    .setRevision(thing2.getRevision().get().increment().increment())
                    .build();
            kit.reply(RetrieveThingResponse.of(thingId, thing2WithUpdatedRev.toJson(
                    thing2WithUpdatedRev.getImplementedSchemaVersion(), FieldType.all()), headers2));
            askResultCached.toCompletableFuture().join();
            assertThat(askResultCached).isCompletedWithValue(getExpectedThingJson());
        });
    }

}
