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
package org.eclipse.ditto.gateway.service.streaming.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.protocol.PartialAccessSignalFilter;
import org.eclipse.ditto.internal.utils.protocol.ReadableSignalView;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests that the extra fields retrieved by the caching enrichment facade for a partially granted subscriber only
 * contain what it may read, even if the facade updates its cache from consecutive events without a round trip.
 */
public final class PartialAccessEnrichmentTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");
    private static final AuthorizationSubject CAROL = AuthorizationSubject.newInstance("nginx:carol");
    private static final AuthorizationContext CAROL_CONTEXT =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, CAROL);
    private static final DittoHeaders PARTIAL_FOR_CAROL = DittoHeaders.newBuilder()
            .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                    "{\"subjects\":[\"nginx:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
            .readGrantedSubjects(List.of(CAROL))
            .build();
    private static final ThingFieldSelector EXTRA_FIELDS =
            ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("attributes"));

    @Test
    public void cachedExtraFieldsOfPartialReaderDoNotContainUnreadableFieldsOfConsecutiveEvents() {
        final AtomicInteger roundTrips = new AtomicInteger();
        // the round trip is a RetrieveThing on behalf of carol, which only returns what carol may read
        final SignalEnrichmentFacade roundTripFacade = (thingId, fields, headers, signal) -> {
            roundTrips.incrementAndGet();
            return CompletableFuture.completedFuture(
                    JsonObject.of("{\"_revision\":3,\"attributes\":{\"pub\":\"p0\"}}"));
        };
        final SignalEnrichmentFacade underTest = DittoCachingSignalEnrichmentFacade.newInstance(roundTripFacade,
                DefaultCacheConfig.of(ConfigFactory.parseString("cache { maximum-size = 10\n" +
                        "expire-after-create = 2m }"), "cache"),
                Runnable::run, "test");
        final DittoHeaders sessionHeaders = DittoHeaders.newBuilder().authorizationContext(CAROL_CONTEXT).build();

        final AttributeModified rev3 = AttributeModified.of(THING_ID, JsonPointer.of("pub"), JsonValue.of("p0"), 3L,
                Instant.EPOCH, PARTIAL_FOR_CAROL, null);
        final AttributesModified rev4 = AttributesModified.of(THING_ID,
                Attributes.newBuilder().set("pub", "p2").set("secret", "s2").build(), 4L, Instant.EPOCH,
                PARTIAL_FOR_CAROL, null);

        assertThat(retrieveExtraFields(underTest, rev3, sessionHeaders))
                .isEqualTo(JsonObject.of("{\"attributes\":{\"pub\":\"p0\"}}"));
        final JsonObject extraOfRev4 = retrieveExtraFields(underTest, rev4, sessionHeaders);

        assertThat(roundTrips).hasValue(1);
        assertThat(extraOfRev4).isEqualTo(JsonObject.of("{\"attributes\":{\"pub\":\"p2\"}}"));
    }

    private static JsonObject retrieveExtraFields(final SignalEnrichmentFacade facade, final Signal<?> event,
            final DittoHeaders sessionHeaders) {
        final ReadableSignalView view = PartialAccessSignalFilter.viewFor(event, CAROL_CONTEXT).orElseThrow();
        return SessionedSignals.of(event, sessionHeaders, null, EXTRA_FIELDS)
                .retrieveExtraFields(facade, view.getSignal())
                .toCompletableFuture()
                .join();
    }

}
