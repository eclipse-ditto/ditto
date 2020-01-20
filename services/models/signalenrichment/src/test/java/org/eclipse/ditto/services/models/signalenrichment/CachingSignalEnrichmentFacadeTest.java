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
        final CacheConfig cacheConfig =
                DefaultCacheConfig.of(ConfigFactory.parseString(CACHE_CONFIG), CACHE_CONFIG_KEY);
        final ByRoundTripSignalEnrichmentFacade cacheLoaderFacade =
                ByRoundTripSignalEnrichmentFacade.of(kit.getRef(), Duration.ofSeconds(10L));
        return CachingSignalEnrichmentFacade.of(cacheLoaderFacade, cacheConfig, kit.getSystem().getDispatcher(),
                "test");
    }

    @Override
    protected JsonObject getThingResponseThingJson() {
        return JsonObject.of("{\n" +
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

    @Test
    public void alreadyLoadedCacheEntryIsReused() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final String userId = "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationSubjects(userId)
                    .correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationSubjects()).contains(userId);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers
                    );

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            assertThat(askResultCached).isCompletedWithValue(getExpectedThingJson());
        });
    }

    @Test
    public void differentAuthSubjectsLeadToCacheRetrievals() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final String userId1 = "user1";
            final String userId2 = "user2";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationSubjects(userId1)
                    .correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationSubjects()).contains(userId1);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead but other auth subjects
            final DittoHeaders headers2 = headers.toBuilder()
                    .authorizationSubjects("user2")
                    .build();
            underTest.retrievePartialThing(thingId, SELECTOR, headers2);

            // THEN: a cache lookup should be done containing the other auth subject header
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing2.getDittoHeaders().getAuthorizationSubjects()).contains(userId2);
            assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
        });
    }

    @Test
    public void differentFieldSelectorsLeadToCacheRetrievals() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.dummy();
            final String userId = "user1";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationSubjects(userId)
                    .correlationId(UUID.randomUUID().toString()).build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers);

            final JsonFieldSelector selector2 =
                    JsonFieldSelector.newInstance("attributes", "features");

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing.getDittoHeaders().getAuthorizationSubjects()).contains(userId);
            assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with different selector for an event with one revision ahead
            underTest.retrievePartialThing(thingId, selector2, headers);

            // THEN: a cache lookup should be done using the other selector
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            assertThat(retrieveThing2.getDittoHeaders().getAuthorizationSubjects()).contains(userId);
            assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(selector2));
        });
    }

}
