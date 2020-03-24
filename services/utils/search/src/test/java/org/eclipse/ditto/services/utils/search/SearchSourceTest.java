/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.search;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.StreamThings;
import org.eclipse.ditto.signals.events.thingsearch.ThingsOutOfSync;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.StreamRefs;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.search.SearchSource}.
 */
public final class SearchSourceTest {

    private static final String SORT = "sort(+/attributes/counter,+/thingId)";
    private static final JsonFieldSelector SORT_FIELDS = JsonFieldSelector.newInstance("attributes/counter", "thingId");

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe pubSubMediatorProbe;
    private TestProbe conciergeForwarderProbe;
    private DittoHeaders dittoHeaders;
    private TestPublisher.Probe<Object> sourceProbe;
    private TestSubscriber.Probe<Object> sinkProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create();
        materializer = ActorMaterializer.create(actorSystem);
        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", actorSystem);
        conciergeForwarderProbe = TestProbe.apply("conciergeForwarder", actorSystem);
        dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
    }

    @After
    public void shutdown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void emptyStream() {
        startTestSearchSource(null, null);
        sinkProbe.request(1L);
        conciergeForwarderProbe.expectMsg(streamThings(null));
        conciergeForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest(); // this request is the input buffer size and not the sink probe request.
        sourceProbe.sendComplete();
        sinkProbe.expectComplete();
    }

    @Test
    public void nonemptyStreamWithPartialDissync() {
        final JsonFieldSelector fields =
                JsonFieldSelector.newInstance("thingId", "_revision", "_modified");
        startTestSearchSource(fields, null);
        sinkProbe.request(200L);
        conciergeForwarderProbe.expectMsg(streamThings(null));
        conciergeForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:3").sendNext("t:2").sendNext("t:1").sendComplete();
        conciergeForwarderProbe.expectMsg(retrieveThing("t:3", fields));
        conciergeForwarderProbe.reply(retrieveThingResponse(3));
        conciergeForwarderProbe.expectMsg(retrieveThing("t:2", fields));
        conciergeForwarderProbe.reply(ThingNotAccessibleException.newBuilder(ThingId.of("t:2")).build());
        conciergeForwarderProbe.expectMsg(retrieveThing("t:1", fields));
        conciergeForwarderProbe.reply(retrieveThingResponse(1));

        // successfully retrieved things are found
        sinkProbe.expectNext(getThing(3).toJson())
                .expectNext(getThing(1).toJson())
                .expectComplete();

        // out-of-sync thing is reported
        pubSubMediatorProbe.expectMsg(DistPubSubAccess.publishViaGroup(ThingsOutOfSync.TYPE,
                ThingsOutOfSync.of(Collections.singletonList(ThingId.of("t:2")), dittoHeaders)));
    }

    @Test
    public void resumeAtStart() {
        final JsonArray sortValues = JsonArray.of(997, "t:3");
        startTestSearchSource(null, sortValues);
        sinkProbe.request(200L);
        conciergeForwarderProbe.expectMsg(streamThings(sortValues));
        conciergeForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:2").sendComplete();
        conciergeForwarderProbe.expectMsg(retrieveThing("t:2", null));
        conciergeForwarderProbe.reply(retrieveThingResponse(2));
        sinkProbe.expectNext(getThing(2).toJson())
                .expectComplete();
    }

    @Test
    public void cursorDeleted() {
        startTestSearchSource(null, null);
        sinkProbe.request(200L);
        conciergeForwarderProbe.expectMsg(streamThings(null));
        conciergeForwarderProbe.reply(materializeSourceProbe());

        // GIVEN: first search result goes through
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:3");
        conciergeForwarderProbe.expectMsg(retrieveThing("t:3", null));
        conciergeForwarderProbe.reply(retrieveThingResponse(3));
        sinkProbe.expectNext(getThing(3).toJson());

        // WHEN: search persistence deleted the cursor
        sourceProbe.sendError(new IllegalStateException("Mock cursor-not-found error"));

        // THEN: the final thing is retrieved again to compute the cursor
        conciergeForwarderProbe.expectMsg(retrieveThing("t:3", SORT_FIELDS));
        conciergeForwarderProbe.reply(retrieveThingResponse(3));

        // THEN: stream resumes from the last result
        conciergeForwarderProbe.expectMsg(streamThings(JsonArray.of(997, "t:3")));
        conciergeForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:2").sendComplete();
        conciergeForwarderProbe.expectMsg(retrieveThing("t:2", null));
        conciergeForwarderProbe.reply(retrieveThingResponse(2));
        sinkProbe.expectNext(getThing(2).toJson())
                .expectComplete();
    }

    @Test
    public void askTimeoutDuringResumption() {
        startTestSearchSource(null, null);
        sinkProbe.request(200L);
        conciergeForwarderProbe.expectMsg(streamThings(null));
        conciergeForwarderProbe.reply(materializeSourceProbe());

        // GIVEN: first search result goes through
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:3");
        conciergeForwarderProbe.expectMsg(retrieveThing("t:3", null));
        conciergeForwarderProbe.reply(retrieveThingResponse(3));
        sinkProbe.expectNext(getThing(3).toJson());

        // WHEN: thing ID source failed and cursor computation failed
        sourceProbe.sendError(new IllegalStateException("Mock SourceRef error"));

        // THEN: source failed
        sinkProbe.expectError();
    }

    private Thing getThing(final int i) {
        return Thing.newBuilder()
                .setId(ThingId.of("t:" + i))
                .setAttributes("{\"counter\":" + (1000 - i) + "}")
                .build();
    }

    private RetrieveThingResponse retrieveThingResponse(final int i) {
        return RetrieveThingResponse.of(ThingId.of("t:" + i), getThing(i), dittoHeaders);
    }

    private RetrieveThing retrieveThing(final String id, @Nullable final JsonFieldSelector fields) {
        return RetrieveThing.getBuilder(ThingId.of(id), dittoHeaders)
                .withSelectedFields(fields)
                .build();
    }

    private SourceRef<Object> materializeSourceProbe() {
        final Pair<TestPublisher.Probe<Object>, CompletionStage<SourceRef<Object>>> materializedValues =
                TestSource.probe(actorSystem).toMat(StreamRefs.sourceRef(), Keep.both()).run(materializer);
        sourceProbe = materializedValues.first();
        return materializedValues.second().toCompletableFuture().join();
    }

    private StreamThings streamThings(@Nullable final JsonArray sortValues) {
        return StreamThings.of(null, null, SORT, sortValues, dittoHeaders);
    }

    private void startTestSearchSource(@Nullable final JsonFieldSelector fields,
            @Nullable final JsonArray sortValues) {
        final SearchSource underTest = SearchSource.newBuilder()
                .pubSubMediator(pubSubMediatorProbe.ref())
                .conciergeForwarder(conciergeForwarderProbe.ref())
                .thingsAskTimeout(Duration.ofSeconds(3L))
                .searchAskTimeout(Duration.ofSeconds(3L))
                .fields(fields)
                .sort(SORT)
                .sortFields(SORT_FIELDS)
                .sortValues(sortValues)
                .dittoHeaders(dittoHeaders)
                .build();
        sinkProbe = underTest.start()
                .map(Object.class::cast)
                .runWith(TestSink.probe(actorSystem), materializer);
    }
}
