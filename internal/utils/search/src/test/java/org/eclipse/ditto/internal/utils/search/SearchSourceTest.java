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
package org.eclipse.ditto.internal.utils.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.StreamRefs;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link SearchSource}.
 */
public final class SearchSourceTest {

    private static final String SORT = "sort(+/attributes/counter,+/thingId)";
    private static final JsonFieldSelector SORT_FIELDS = JsonFieldSelector.newInstance("attributes/counter", "thingId");

    private ActorSystem actorSystem;
    private Materializer materializer;
    private TestProbe pubSubMediatorProbe;
    private TestProbe edgeCommandForwarderProbe;
    private DittoHeaders dittoHeaders;
    private TestPublisher.Probe<Object> sourceProbe;
    private TestSubscriber.Probe<Object> sinkProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create();
        materializer = SystemMaterializer.get(actorSystem).materializer();
        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", actorSystem);
        edgeCommandForwarderProbe = TestProbe.apply("edgeCommandForwarder", actorSystem);
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
        edgeCommandForwarderProbe.expectMsg(streamThings(null));
        edgeCommandForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest(); // this request is the input buffer size and not the sink probe request.
        sourceProbe.sendComplete();
        sinkProbe.expectComplete();
    }

    @Test
    public void clientError() {
        startTestSearchSource(null, null);
        sinkProbe.request(1L);
        edgeCommandForwarderProbe.expectMsg(streamThings(null));
        edgeCommandForwarderProbe.reply(InvalidRqlExpressionException.newBuilder().build());
        assertThat(sinkProbe.expectError()).isInstanceOf(InvalidRqlExpressionException.class);
    }

    @Test
    public void nonemptyStreamWithPartialDissync() {
        final JsonFieldSelector fields =
                JsonFieldSelector.newInstance("thingId", "_revision", "_modified");
        startTestSearchSource(fields, null);
        sinkProbe.request(200L);
        edgeCommandForwarderProbe.expectMsg(streamThings(null));
        edgeCommandForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:3").sendNext("t:2").sendNext("t:1").sendComplete();
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:3", fields));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(3));
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:2", fields));
        edgeCommandForwarderProbe.reply(ThingNotAccessibleException.newBuilder(ThingId.of("t:2")).build());
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:1", fields));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(1));

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
        edgeCommandForwarderProbe.expectMsg(streamThings(sortValues));
        edgeCommandForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:2").sendComplete();
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:2", null));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(2));
        sinkProbe.expectNext(getThing(2).toJson())
                .expectComplete();
    }

    @Test
    public void cursorDeleted() {
        // Turn off logging to suppress stack trace. Comment out to debug.
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());

        startTestSearchSource(null, null);
        sinkProbe.request(200L);
        edgeCommandForwarderProbe.expectMsg(streamThings(null));
        edgeCommandForwarderProbe.reply(materializeSourceProbe());

        // GIVEN: first search result goes through
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:3");
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:3", null));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(3));
        sinkProbe.expectNext(getThing(3).toJson());

        // WHEN: search persistence deleted the cursor
        sourceProbe.sendError(new IllegalStateException("mock cursor-not-found error"));

        // THEN: the final thing is retrieved again to compute the cursor
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:3", SORT_FIELDS));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(3));

        // THEN: stream resumes from the last result
        edgeCommandForwarderProbe.expectMsg(streamThings(JsonArray.of(997, "t:3")));
        edgeCommandForwarderProbe.reply(materializeSourceProbe());
        sourceProbe.expectRequest();
        sourceProbe.sendNext("t:2").sendComplete();
        edgeCommandForwarderProbe.expectMsg(retrieveThing("t:2", null));
        edgeCommandForwarderProbe.reply(retrieveThingResponse(2));
        sinkProbe.expectNext(getThing(2).toJson())
                .expectComplete();
    }

    private Thing getThing(final int i) {
        return Thing.newBuilder()
                .setId(ThingId.of("t:" + i))
                .setAttributes("{\"counter\":" + (1000 - i) + "}")
                .build();
    }

    private RetrieveThingResponse retrieveThingResponse(final int i) {
        return RetrieveThingResponse.of(ThingId.of("t:" + i), getThing(i), null, null, dittoHeaders);
    }

    private RetrieveThing retrieveThing(final String id, @Nullable final JsonFieldSelector fields) {
        return RetrieveThing.getBuilder(ThingId.of(id), dittoHeaders)
                .withSelectedFields(fields)
                .build();
    }

    private SourceRef<Object> materializeSourceProbe() {
        final Pair<TestPublisher.Probe<Object>, SourceRef<Object>> materializedValues =
                TestSource.probe(actorSystem).toMat(StreamRefs.sourceRef(), Keep.both()).run(materializer);
        sourceProbe = materializedValues.first();
        return materializedValues.second();
    }

    private StreamThings streamThings(@Nullable final JsonArray sortValues) {
        return StreamThings.of(null, null, SORT, sortValues, dittoHeaders);
    }

    private void startTestSearchSource(@Nullable final JsonFieldSelector fields,
            @Nullable final JsonArray sortValues) {
        final SearchSource underTest = SearchSource.newBuilder()
                .pubSubMediator(pubSubMediatorProbe.ref())
                .commandForwarder(ActorSelection.apply(edgeCommandForwarderProbe.ref(), ""))
                .thingsAskTimeout(Duration.ofSeconds(3L))
                .searchAskTimeout(Duration.ofSeconds(3L))
                .fields(fields)
                .sort(SORT)
                .sortValues(sortValues)
                .dittoHeaders(dittoHeaders)
                .build();
        sinkProbe = underTest.start(
                builder -> builder.minBackoff(Duration.ZERO).maxBackoff(Duration.ZERO))
                .map(Object.class::cast)
                .runWith(TestSink.probe(actorSystem), materializer);
    }
}
