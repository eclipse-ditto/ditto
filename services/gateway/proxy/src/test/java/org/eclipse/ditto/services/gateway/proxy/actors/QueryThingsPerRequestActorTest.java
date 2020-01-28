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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThings;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.gateway.proxy.actors.QueryThingsPerRequestActor}.
 */
public final class QueryThingsPerRequestActorTest {

    private ActorSystem actorSystem;
    private TestProbe aggregatorProbe;
    private TestProbe originalSenderProbe;
    private TestProbe pubSubMediatorProbe;
    private DittoHeaders dittoHeaders;

    @Before
    public void startActorSystem() {
        actorSystem = ActorSystem.create();
        aggregatorProbe = TestProbe.apply("aggregator", actorSystem);
        originalSenderProbe = TestProbe.apply("originalSender", actorSystem);
        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", actorSystem);
        dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void sendNoRetrieveThingsOnEmptySearchResult() {
        final ActorRef underTest = createQueryThingsPerRequestActor(QueryThings.of(dittoHeaders));
        final QueryThingsResponse emptyResponse =
                QueryThingsResponse.of(SearchResult.newBuilder().build(), dittoHeaders);

        // WHEN
        underTest.tell(emptyResponse, ActorRef.noSender());

        // THEN
        originalSenderProbe.expectMsg(emptyResponse);
    }

    @Test
    public void reconstructsQueryThingsResponseByAskingRetrieveThings() {
        final ActorRef underTest = createQueryThingsPerRequestActor(QueryThings.of(dittoHeaders));
        final ThingId thingId1 = ThingId.of("thing:1");
        final ThingId thingId2 = ThingId.of("thing:2");
        final SearchResult searchResult = forIdItems(thingId1, thingId2);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, dittoHeaders);

        // WHEN: QueryThingsResponse has items
        underTest.tell(queryThingsResponse, ActorRef.noSender());

        // THEN: aggregator is a asked to retrieve things
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .dittoHeaders(dittoHeaders)
                .build());
        aggregatorProbe.reply(RetrieveThingsResponse.of(asArray(thingId1, thingId2), "thing", dittoHeaders));
        originalSenderProbe.expectMsg(queryThingsResponse);
    }

    @Test
    public void alwaysIncludeThingIdsInInternalRoundTripOnly() {
        // GIVEN: QueryThings selected a field other than thingId
        final JsonFieldSelector fields = JsonFieldSelector.newInstance("definition");
        final JsonFieldSelector fieldsWithId = JsonFieldSelector.newInstance("thingId", "definition");
        final QueryThings queryThings = QueryThings.of(null, null, fields, null, dittoHeaders);
        final ActorRef underTest = createQueryThingsPerRequestActor(queryThings);
        final ThingId thingId1 = ThingId.of("thing:1");
        final ThingId thingId2 = ThingId.of("thing:2");
        final SearchResult searchResult = forIdItems(thingId1, thingId2);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, dittoHeaders);

        // WHEN: QueryThingsResponse has items
        underTest.tell(queryThingsResponse, ActorRef.noSender());

        // THEN: aggregator is asked to retrieve things with selected fields including thingId
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .selectedFields(fieldsWithId)
                .dittoHeaders(dittoHeaders)
                .build());
        final JsonObject definition = JsonObject.newBuilder().set("definition", "vacuum:cleaner:1548").build();
        aggregatorProbe.reply(
                RetrieveThingsResponse.of(asArrayWithExtra(definition, thingId1, thingId2), "thing", dittoHeaders));

        // THEN: final response does not include thingId
        originalSenderProbe.expectMsg(
                QueryThingsResponse.of(SearchResult.newBuilder().add(definition, definition).build(), dittoHeaders));
    }

    @Test
    public void reportOutOfSyncThings() {
        final ActorRef underTest = createQueryThingsPerRequestActor(QueryThings.of(dittoHeaders));
        final ThingId thingId1 = ThingId.of("thing:1");
        final ThingId thingId2 = ThingId.of("thing:2");
        final SearchResult searchResult = forIdItems(thingId1, thingId2);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, dittoHeaders);

        // GIVEN: QueryThingsResponse has items and aggregator probe is asked to retrieve things
        underTest.tell(queryThingsResponse, ActorRef.noSender());
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .dittoHeaders(dittoHeaders)
                .build());

        // WHEN: response from aggregator is missing thingId1
        aggregatorProbe.reply(RetrieveThingsResponse.of(asArray(thingId2), "thing", dittoHeaders));

        // THEN: final response does not include thingId1
        originalSenderProbe.expectMsg(QueryThingsResponse.of(forIdItems(thingId2), dittoHeaders));

        // THEN: an UpdateThings command is published requesting search index update of thingId1
        pubSubMediatorProbe.expectMsg(
                DistPubSubAccess.publishViaGroup(
                        UpdateThings.TYPE,
                        UpdateThings.of(List.of(thingId1), dittoHeaders)
                )
        );
    }

    private ActorRef createQueryThingsPerRequestActor(final QueryThings queryThings) {
        final Props props = QueryThingsPerRequestActor.props(
                queryThings,
                aggregatorProbe.ref(),
                originalSenderProbe.ref(),
                pubSubMediatorProbe.ref()
        );
        return actorSystem.actorOf(props);
    }

    private static SearchResult forIdItems(final ThingId... thingIds) {
        return SearchResult.newBuilder().addAll(asArray(thingIds)).build();
    }

    private static JsonArray asArray(final ThingId... thingIds) {
        return asArrayWithExtra(JsonObject.empty(), thingIds);
    }

    private static JsonArray asArrayWithExtra(final JsonObject extra, final ThingId... thingIds) {
        return Arrays.stream(thingIds)
                .map(QueryThingsPerRequestActorTest::idItem)
                .map(object -> object.toBuilder().setAll(extra).build())
                .collect(JsonCollectors.valuesToArray());

    }

    private static JsonObject idItem(final ThingId thingId) {
        return JsonObject.newBuilder()
                .set(Thing.JsonFields.ID, thingId.toString())
                .build();
    }

}
