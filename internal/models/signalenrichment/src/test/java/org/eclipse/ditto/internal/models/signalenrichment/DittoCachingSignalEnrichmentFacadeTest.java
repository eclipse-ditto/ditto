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

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.DittoTestSystem;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSelection;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link DittoCachingSignalEnrichmentFacade}.
 */
public final class DittoCachingSignalEnrichmentFacadeTest extends AbstractSignalEnrichmentFacadeTest {

    private static final String CACHE_CONFIG_KEY = "my-cache";
    private static final String CACHE_CONFIG = CACHE_CONFIG_KEY + """
            {
              maximum-size = 10
              expire-after-create = 2m
            }
            """;
    private static final String ISSUER_PREFIX = "test:";

    private static final JsonObject THING_RESPONSE_JSON = JsonObject.of("""
            {
              "_revision": 3,
              "policyId": "policy:id",
              "attributes": {"x": 5},
              "features": {"y": {"properties": {"z": true}}},
              "_metadata": {"attributes": {"x": {"type": "x attribute"}}}
            }""");
    private static final JsonObject EXPECTED_THING_JSON = JsonObject.of("""
            {
              "policyId": "policy:id",
              "attributes": {"x":  5},
              "features": {"y": {"properties": {"z":  true}}},
              "_metadata": {"attributes": {"x": {"type": "x attribute"}}}
            }""");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Override
    protected SignalEnrichmentFacade createSignalEnrichmentFacadeUnderTest(final TestKit kit, final Duration duration) {
        final CacheConfig cacheConfig =
                DefaultCacheConfig.of(ConfigFactory.parseString(CACHE_CONFIG), CACHE_CONFIG_KEY);
        final ActorSelection commandHandler = ActorSelection.apply(kit.getRef(), "");
        final ByRoundTripSignalEnrichmentFacade cacheLoaderFacade =
                ByRoundTripSignalEnrichmentFacade.of(commandHandler, Duration.ofSeconds(10L));
        return DittoCachingSignalEnrichmentFacade.newInstance(
                cacheLoaderFacade,
                cacheConfig,
                kit.getSystem().getDispatcher(),
                "test");
    }

    @Override
    protected JsonObject getThingResponseThingJson() {
        return THING_RESPONSE_JSON;
    }

    @Override
    protected JsonObject getExpectedThingJson() {
        return EXPECTED_THING_JSON;
    }

    @Override
    protected JsonFieldSelector actualSelectedFields(final JsonFieldSelector selector) {
        return JsonFactory.newFieldSelectorBuilder()
                .addPointers(selector)
                .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                .build();
    }

    @Test
    public void alreadyLoadedCacheEntryIsReused() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers,
                            THING_EVENT.setRevision(THING_EVENT.getRevision() + 1));

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            softly.assertThat(askResultCached).isCompletedWithValue(getExpectedThingJson());
        });
    }

    @Test
    public void alreadyLoadedCacheEntryIsReusedForMergedEvent() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final ThingMerged mergeAttributes = ThingMerged.of(thingId, JsonPointer.of("/attributes"),
                    JsonObject.newBuilder()
                            .set("x", 42)
                            .set("foo", "bar")
                            .build(),
                    THING_EVENT.getRevision() + 1,
                    null,
                    DittoHeaders.empty(),
                    null
            );
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, mergeAttributes);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the merge update updated value:
            final JsonObject expectedThingJson = EXPECTED_THING_JSON.toBuilder()
                    .set("/attributes/x", 42)
                    .build();
            softly.assertThat(askResultCached).isCompletedWithValue(expectedThingJson);

            // WHEN: then the attribute "x" is modified with a merge:
            final ThingMerged mergeAttributeX = ThingMerged.of(thingId, JsonPointer.of("/attributes/x"),
                    JsonValue.of(1337),
                    mergeAttributes.getRevision() + 1,
                    null,
                    DittoHeaders.empty(),
                    null
            );
            final CompletionStage<JsonObject> askResultCached2 =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, mergeAttributeX);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached2.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the merge update updated value:
            final JsonObject expectedThingJson2 = EXPECTED_THING_JSON.toBuilder()
                    .set("/attributes/x", 1337)
                    .build();
            softly.assertThat(askResultCached2).isCompletedWithValue(expectedThingJson2);
        });
    }

    @Test
    public void alreadyLoadedCacheEntryIsReusedForMergedEventOnRootLevel() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final ThingMerged mergeAttributes = ThingMerged.of(thingId, JsonPointer.of("/"),
                    JsonObject.newBuilder()
                            .set("attributes",
                                    JsonObject.newBuilder()
                                            .set("x", 42)
                                            .set("foo", "bar")
                                            .build())
                            .build(),
                    THING_EVENT.getRevision() + 1,
                    null,
                    DittoHeaders.empty(),
                    null
            );
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, mergeAttributes);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
        });
    }

    @Test
    public void alreadyLoadedCacheEntryIsInvalidatedForUnexpectedEventRevision() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final DittoHeaders headers = DittoHeaders.newBuilder().randomCorrelationId().build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector with event with 2 revisions ahead
            final DittoHeaders headers2 = DittoHeaders.newBuilder().randomCorrelationId().build();
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers2,
                            THING_EVENT.setRevision(THING_EVENT.getRevision() + 2)); // notice +2 here

            // THEN: do another cache lookup after invalidation
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            final Thing thing2 = ThingsModelFactory.newThing(getThingResponseThingJson());
            final Thing thing2WithUpdatedRev = thing2.toBuilder()
                    .setRevision(thing2.getRevision().get().increment().increment())
                    .build();
            kit.reply(RetrieveThingResponse.of(thingId, thing2WithUpdatedRev.toJson(
                    thing2WithUpdatedRev.getImplementedSchemaVersion(), FieldType.all()), headers2));
            askResultCached.toCompletableFuture().join();
            softly.assertThat(askResultCached).isCompletedWithValue(getExpectedThingJson());
        });
    }

    @Test
    public void differentAuthSubjectsLeadToCacheRetrievals() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId1 = ISSUER_PREFIX + "user1";
            final String userId2 = ISSUER_PREFIX + "user2";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId1)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId1);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead but other auth subjects
            final DittoHeaders headers2 = headers.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(ISSUER_PREFIX + "user2")
                    ))
                    .build();
            underTest.retrievePartialThing(thingId, SELECTOR, headers2,
                    THING_EVENT.setRevision(THING_EVENT.getRevision() + 1));

            // THEN: a cache lookup should be done containing the other auth subject header
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing2.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId2);
            softly.assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
        });
    }

    @Test
    public void differentFieldSelectorsLeadToCacheRetrievals() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user1";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            final JsonFieldSelector selector2 = JsonFieldSelector.newInstance("attributes", "features");

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();
            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with different selector for an event with one revision ahead
            underTest.retrievePartialThing(thingId, selector2, headers,
                    THING_EVENT.setRevision(THING_EVENT.getRevision() + 1));

            // THEN: a cache lookup should be done using the other selector
            final RetrieveThing retrieveThing2 = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing2.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing2.getSelectedFields()).contains(actualSelectedFields(selector2));
        });
    }

    @Test
    public void metadataIsUpdatedForMergedEvent() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();

            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final ThingMerged mergeAttribute = ThingMerged.of(thingId, JsonPointer.of("/attributes/x"),
                    JsonFactory.newValue(6),
                    THING_EVENT.getRevision() + 1,
                    null,
                    DittoHeaders.empty(),
                    Metadata.newMetadata(JsonObject.newBuilder()
                            .set("type", "x is now y attribute")
                            .build()
                    )
            );
            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, mergeAttribute);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the merged metadata updated value:
            final JsonObject expectedThingJson = EXPECTED_THING_JSON.toBuilder()
                    .set("attributes", JsonObject.newBuilder()
                            .set("x", 6)
                            .build())
                    .set("_metadata", JsonObject.newBuilder()
                            .set("attributes", JsonObject.newBuilder()
                                    .set("x", JsonObject.newBuilder()
                                            .set("type", "x is now y attribute")
                                            .build())
                                    .build())
                            .build())
                    .build();

            softly.assertThat(askResultCached).isCompletedWithValue(expectedThingJson);
        });
    }

    @Test
    public void metadataIsDeletedForDeletedEvent() {
        DittoTestSystem.run(this, kit -> {
            // GIVEN: SignalEnrichmentFacade.retrievePartialThing()
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final CompletionStage<JsonObject> askResult =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, THING_EVENT);

            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(SELECTOR));
            // WHEN: response is handled so that it is also added to the cache
            kit.reply(RetrieveThingResponse.of(thingId, getThingResponseThingJson(), headers));
            askResult.toCompletableFuture().join();

            softly.assertThat(askResult).isCompletedWithValue(getExpectedThingJson());

            // WHEN: same thing is asked again with same selector for an event with one revision ahead
            final AttributeDeleted attributeDeleted =
                    AttributeDeleted.of(thingId, JsonPointer.of("/x"),
                            THING_EVENT.getRevision() + 1,
                            null,
                            DittoHeaders.empty(),
                            MetadataModelFactory.nullMetadata());

            final CompletionStage<JsonObject> askResultCached =
                    underTest.retrievePartialThing(thingId, SELECTOR, headers, attributeDeleted);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResultCached.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the merged metadata updated value:
            final JsonObject expectedThingJson = EXPECTED_THING_JSON.toBuilder()
                    .remove("attributes")
                    .set("_metadata", JsonObject.newBuilder()
                            .set("attributes", JsonObject.newBuilder().build())
                            .build())
                    .build();

            softly.assertThat(askResultCached).isCompletedWithValue(expectedThingJson);
        });
    }

}
