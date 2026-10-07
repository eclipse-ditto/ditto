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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamingSession;
import org.eclipse.ditto.gateway.service.streaming.signals.IncomingSignal;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests the evaluation of the change filter of a WebSocket session in {@link WebSocketRoute}.
 */
public final class WebSocketRouteChangeFilterTest extends EndpointTestBase {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:thing");

    @Test
    public void nonMatchingChangeFilterSkipsEnrichmentAndIssuesWeakAcknowledgements() throws Exception {
        final TestKit sessionActor = new TestKit(system());
        final FeaturePropertyModified event = featurePropertyModified(DittoHeaders.newBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("custom-ack")))
                .build());
        final SessionedJsonifiable sessionedJsonifiable = sessioned(event, false, sessionActor);
        final SignalEnrichmentFacade facade = Mockito.mock(SignalEnrichmentFacade.class);

        final Collection<String> published = postprocess(sessionedJsonifiable, facade, sessionActor);

        assertThat(published).isEmpty();
        Mockito.verify(sessionedJsonifiable, Mockito.never()).retrieveExtraFields(Mockito.any());
        Mockito.verifyNoInteractions(facade);
        Mockito.verify(sessionedJsonifiable).finishSpan();
        final IncomingSignal weakAck = sessionActor.expectMsgClass(IncomingSignal.class);
        assertThat(weakAck.getSignal()).isInstanceOfSatisfying(Acknowledgement.class, ack -> {
            assertThat(ack.getLabel().toString()).isEqualTo("custom-ack");
            assertThat(ack.isWeak()).isTrue();
        });
    }

    @Test
    public void matchingChangeFilterRetrievesExtraFields() throws Exception {
        final TestKit sessionActor = new TestKit(system());
        final FeaturePropertyModified event = featurePropertyModified(DittoHeaders.empty());
        final SessionedJsonifiable sessionedJsonifiable = sessioned(event, true, sessionActor);
        final SignalEnrichmentFacade facade = Mockito.mock(SignalEnrichmentFacade.class);

        postprocess(sessionedJsonifiable, facade, sessionActor);

        Mockito.verify(sessionedJsonifiable).retrieveExtraFields(facade);
    }

    private Collection<String> postprocess(final SessionedJsonifiable sessionedJsonifiable,
            final SignalEnrichmentFacade facade, final TestKit streamingActor) throws Exception {
        final WebSocketRoute underTest = WebSocketRoute.getInstance(system(), streamingActor.getRef(),
                streamingConfig, SystemMaterializer.get(system()).materializer());
        return underTest.postprocess(DittoProtocolAdapter.newInstance(), facade,
                        DittoLoggerFactory.getThreadSafeLogger(WebSocketRouteChangeFilterTest.class))
                .apply(sessionedJsonifiable)
                .toCompletableFuture()
                .join();
    }

    private static SessionedJsonifiable sessioned(final FeaturePropertyModified event,
            final boolean matchesChangeFilter, final TestKit sessionActor) {
        final StreamingSession session = Mockito.mock(StreamingSession.class);
        Mockito.when(session.matchesChangeFilter(event)).thenReturn(matchesChangeFilter);
        Mockito.when(session.getStreamingSessionActor()).thenReturn(sessionActor.getRef());
        final SessionedJsonifiable sessionedJsonifiable = Mockito.mock(SessionedJsonifiable.class);
        Mockito.when(sessionedJsonifiable.getJsonifiable()).thenReturn(event);
        Mockito.when(sessionedJsonifiable.getDittoHeaders()).thenReturn(event.getDittoHeaders());
        Mockito.when(sessionedJsonifiable.getSession()).thenReturn(Optional.of(session));
        Mockito.when(sessionedJsonifiable.retrieveExtraFields(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(JsonObject.empty()));
        return sessionedJsonifiable;
    }

    private static FeaturePropertyModified featurePropertyModified(final DittoHeaders dittoHeaders) {
        return FeaturePropertyModified.of(THING_ID, "otherFeature", JsonPointer.of("value"), JsonValue.of(42), 3L,
                Instant.now(), dittoHeaders, null);
    }

}
