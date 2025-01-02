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
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey(),
                            "[\"/definition\",\"/attributes/pre\",\"/attributes/pre2\"]")
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey(),
                            "{\"/definition\":[\"test:user\"],\"/attributes/pre\":[\"test:user\"]}")
                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey(),
                            "{\"definition\":\"some:cool:definition\",\"attributes\":{\"pre\":{\"bar\": [1,2,3]}}}")
                    .build(),
            MetadataModelFactory.newMetadataBuilder()
                    .set("type", "x attribute")
                    .build());

    private static final JsonFieldSelector SELECTOR_PRE_DEFINED_EXTRA_FIELDS =
            JsonFieldSelector.newInstance("definition", "attributes/pre", "attributes/pre2");


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
        DittoTestSystem.run(this, kit -> {
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
                    underTest.retrievePartialThing(thingId, SELECTOR_PRE_DEFINED_EXTRA_FIELDS, headers,
                            THING_EVENT_PRE_DEFINED_EXTRA_FIELDS);

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

}
