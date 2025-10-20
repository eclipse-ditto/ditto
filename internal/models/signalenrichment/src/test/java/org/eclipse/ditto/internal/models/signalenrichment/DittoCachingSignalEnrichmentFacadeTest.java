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
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.DittoTestSystem;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.Test;

/**
 * Unit tests for {@link DittoCachingSignalEnrichmentFacade}.
 */
public final class DittoCachingSignalEnrichmentFacadeTest extends AbstractCachingSignalEnrichmentFacadeTest {

    private static final JsonObject EXPECTED_THING_JSON = JsonObject.of("""
            {
              "policyId": "policy:id",
              "attributes": {"x":  5},
              "features": {"y": {"properties": {"z":  true}}},
              "_metadata": {"attributes": {"x": {"type": "x attribute"}}}
            }"""
    );

    private static final JsonObject EXPECTED_THING_JSON_PRE_DEFINED_EXTRA = JsonObject.of("""
            {
              "definition": "some:cool:definition",
              "attributes": {"x":  5, "pre": {"bar": [1,2,3]}, "pre2": {"some": 41, "secret": true}}
            }"""
    );

    private static final AttributeModified THING_EVENT_PRE_DEFINED_EXTRA_FIELDS = AttributeModified.of(
            ThingId.generateRandom("org.eclipse.test"),
            JsonPointer.of("x"),
            JsonValue.of(42),
            4L,
            Instant.EPOCH,
            DittoHeaders.newBuilder()
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey(), """
                            ["/definition","/attributes/pre","/attributes/pre2","/attributes/folder"]
                            """)
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey(), """
                                    {"/definition":["test:user"],"/attributes/pre":["test:user"],"/attributes/folder":["test:user"],"/attributes/folder/public":["test:limited"]}
                                    """)
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey(), """
                                    {"definition":"some:cool:definition","attributes":{"pre":{"bar": [1,2,3]},"folder":{"public":"public","private":"private"}}}
                                    """)
                    .build(),
            MetadataModelFactory.newMetadataBuilder()
                    .set("type", "x attribute")
                    .build());


    @Override
    protected CachingSignalEnrichmentFacade createCachingSignalEnrichmentFacade(final TestKit kit,
            final ByRoundTripSignalEnrichmentFacade cacheLoaderFacade, final CacheConfig cacheConfig) {
        return DittoCachingSignalEnrichmentFacade.newInstance(
                cacheLoaderFacade,
                cacheConfig,
                kit.getSystem().getDispatcher(),
                "test");
    }

    @Override
    protected JsonObject getExpectedThingJson() {
        return EXPECTED_THING_JSON;
    }

    @Test
    public void enrichedEventWithPreDefinedExtraFieldsDoesNotLeadToCacheLookup() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final JsonFieldSelector fieldSelector =
                    JsonFieldSelector.newInstance("definition", "attributes/pre", "attributes/pre2");
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, fieldSelector,
                    headers, THING_EVENT_PRE_DEFINED_EXTRA_FIELDS);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResult.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the updated value:
            final JsonObject expectedThingJson = EXPECTED_THING_JSON_PRE_DEFINED_EXTRA.toBuilder()
                    .remove("attributes/x")    // x was not asked for in extra fields
                    .remove("attributes/pre2") // we don't have the read grant for this field
                    .build();
            softly.assertThat(askResult).isCompletedWithValue(expectedThingJson);
        });
    }

    @Test
    public void enrichedEventWithPreDefinedExtraFieldsAndAdditionalRequestedOnesLeadToPartialCacheLookup() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final JsonFieldSelector fieldSelector =
                    JsonFieldSelector.newInstance("definition", "attributes/x", "attributes/unchanged",
                            "attributes/pre", "attributes/pre2");
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, fieldSelector,
                    headers, THING_EVENT_PRE_DEFINED_EXTRA_FIELDS);

            final JsonFieldSelector askedForFieldSelector =
                    JsonFieldSelector.newInstance("attributes/x", "attributes/unchanged");
            // WHEN: Command handler receives expected RetrieveThing and responds with RetrieveThingResponse
            final RetrieveThing retrieveThing = kit.expectMsgClass(RetrieveThing.class);
            softly.assertThat(retrieveThing.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds())
                    .contains(userId);
            softly.assertThat(retrieveThing.getSelectedFields()).contains(actualSelectedFields(askedForFieldSelector));
            // WHEN: response is handled so that it is also added to the cache
            final JsonObject retrievedExtraThing = JsonObject.of("""
                    {
                      "_revision": 3,
                      "attributes": {"x": 42, "unchanged": "foo"}
                    }
                    """);
            kit.reply(RetrieveThingResponse.of(thingId, retrievedExtraThing, headers));
            askResult.toCompletableFuture().join();

            // AND: the resulting thing JSON includes the with the updated value:
            final JsonObject expectedThingJson = EXPECTED_THING_JSON_PRE_DEFINED_EXTRA.toBuilder()
                    .remove("attributes/pre2") // we don't have the read grant for this field
                    .set(JsonPointer.of("attributes/x"),
                            42) // we expect the updated value (as part of the modify event)
                    .set(JsonPointer.of("attributes/unchanged"),
                            "foo") // we expect the updated value (retrieved via cache)
                    .build();
            softly.assertThat(askResult).isCompletedWithValue(expectedThingJson);
        });
    }

    @Test
    public void enrichedEventWithPreDefinedExtraFieldsWithMoreComplexStructure() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "user";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final JsonFieldSelector fieldSelector =
                    JsonFieldSelector.newInstance("attributes/folder");
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, fieldSelector,
                    headers, THING_EVENT_PRE_DEFINED_EXTRA_FIELDS);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResult.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the updated value:
            final JsonObject expectedThingJson = JsonObject.of("""
            {
              "attributes": {"folder": {"public": "public", "private": "private"}}
            }"""
                    );
            softly.assertThat(askResult).isCompletedWithValue(expectedThingJson);
        });
    }

    @Test
    public void enrichedEventWithPreDefinedExtraFieldsWithMoreComplexStructureLimitedUser() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            final SignalEnrichmentFacade underTest =
                    createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));
            final ThingId thingId = ThingId.generateRandom();
            final String userId = ISSUER_PREFIX + "limited";
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance(userId)))
                    .randomCorrelationId()
                    .build();
            final JsonFieldSelector fieldSelector =
                    JsonFieldSelector.newInstance("attributes/folder");
            final CompletionStage<JsonObject> askResult = underTest.retrievePartialThing(thingId, fieldSelector,
                    headers, THING_EVENT_PRE_DEFINED_EXTRA_FIELDS);

            // THEN: no cache lookup should be done
            kit.expectNoMessage(Duration.ofSeconds(1));
            askResult.toCompletableFuture().join();
            // AND: the resulting thing JSON includes the with the updated value:
            final JsonObject expectedThingJson = JsonObject.of("""
            {
              "attributes": {"folder": {"public": "public"}}
            }"""
                    );
            softly.assertThat(askResult).isCompletedWithValue(expectedThingJson);
        });
    }

}
