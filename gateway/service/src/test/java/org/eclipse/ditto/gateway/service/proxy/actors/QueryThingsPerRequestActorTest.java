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
package org.eclipse.ditto.gateway.service.proxy.actors;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.eclipse.ditto.thingsearch.model.SearchResult;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link QueryThingsPerRequestActor}.
 */
public final class QueryThingsPerRequestActorTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(ConfigFactory.load("test"));

    private static GatewayConfig gatewayConfig;

    private TestProbe aggregatorProbe;
    private TestProbe originalSenderProbe;
    private TestProbe pubSubMediatorProbe;
    private DittoHeaders dittoHeaders;
    private DittoHeaders responseHeaders;

    @BeforeClass
    public static void beforeClass() {
        gatewayConfig = DittoGatewayConfig.of(
                DefaultScopedConfig.dittoScoped(ConfigFactory.load("test.conf")));
    }

    @Before
    public void startActorSystem() {
        aggregatorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe("aggregator");
        originalSenderProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe("originalSender");
        pubSubMediatorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe("pubSubMediator");
        dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().responseRequired(true).build();
        responseHeaders = dittoHeaders.toBuilder().responseRequired(false).build();
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
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, responseHeaders);

        // WHEN: QueryThingsResponse has items
        underTest.tell(queryThingsResponse, ActorRef.noSender());

        // THEN: aggregator is asked to retrieve things
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .dittoHeaders(dittoHeaders)
                .build());
        aggregatorProbe.reply(RetrieveThingsResponse.of(asArray(thingId1, thingId2), "thing", responseHeaders));
        originalSenderProbe.expectMsg(queryThingsResponse);
    }

    @Test
    public void thingIdIsIncludedWhenOnlyThingIdIsSelected() {
        // GIVEN: QueryThings selected thingId field
        final JsonFieldSelector fields = JsonFieldSelector.newInstance("thingId");
        final QueryThings queryThings = QueryThings.of(null, null, fields, null, dittoHeaders);
        final ActorRef underTest = createQueryThingsPerRequestActor(queryThings);
        final ThingId thingId1 = ThingId.of("thing:1");
        final ThingId thingId2 = ThingId.of("thing:2");
        final SearchResult searchResult = forIdItems(thingId1, thingId2);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, responseHeaders);

        // WHEN: QueryThingsResponse has items
        underTest.tell(queryThingsResponse, ActorRef.noSender());

        // THEN: aggregator is NOT asked to retrieve things - as asking for only the thingId (which is already known) is
        // a waste of resources
        aggregatorProbe.expectNoMessage();

        // THEN: final response does include the found thingIds
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
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, responseHeaders);

        // WHEN: QueryThingsResponse has items
        underTest.tell(queryThingsResponse, ActorRef.noSender());

        // THEN: aggregator is asked to retrieve things with selected fields including thingId
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .selectedFields(fieldsWithId)
                .dittoHeaders(dittoHeaders)
                .build());
        final JsonObject definition = JsonObject.newBuilder().set("definition", "vacuum:cleaner:1548").build();
        aggregatorProbe.reply(
                RetrieveThingsResponse.of(asArrayWithExtra(definition, thingId1, thingId2), "thing", responseHeaders));

        // THEN: final response does not include thingId
        originalSenderProbe.expectMsg(
                QueryThingsResponse.of(SearchResult.newBuilder().add(definition, definition).build(), responseHeaders));
    }

    @Test
    public void reportOutOfSyncThings() {
        final ActorRef underTest = createQueryThingsPerRequestActor(QueryThings.of(dittoHeaders));
        final ThingId thingId1 = ThingId.of("thing:1");
        final ThingId thingId2 = ThingId.of("thing:2");
        final SearchResult searchResult = forIdItems(thingId1, thingId2);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, responseHeaders);

        // GIVEN: QueryThingsResponse has items and aggregator probe is asked to retrieve things
        underTest.tell(queryThingsResponse, ActorRef.noSender());
        aggregatorProbe.expectMsg(RetrieveThings.getBuilder(thingId1, thingId2)
                .dittoHeaders(dittoHeaders)
                .build());

        // WHEN: response from aggregator is missing thingId1
        aggregatorProbe.reply(RetrieveThingsResponse.of(asArray(thingId2), "thing", responseHeaders));

        // THEN: final response does not include thingId1
        originalSenderProbe.expectMsg(QueryThingsResponse.of(forIdItems(thingId2), responseHeaders));

        // THEN: an UpdateThings command is published requesting search index update of thingId1
        pubSubMediatorProbe.expectMsg(
                DistPubSubAccess.publishViaGroup(
                        ThingsOutOfSync.TYPE,
                        ThingsOutOfSync.of(List.of(thingId1), dittoHeaders)
                )
        );
    }

    @Test
    public void actorShutsDownAfterServiceRequestDoneMessageWasReceived() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final ActorRef underTest = createQueryThingsPerRequestActor(QueryThings.of(dittoHeaders));
            final ThingId thingId1 = ThingId.of("thing:1");
            final ThingId thingId2 = ThingId.of("thing:2");
            final SearchResult searchResult = forIdItems(thingId1, thingId2);
            final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(searchResult, responseHeaders);

            watch(underTest);
            underTest.tell(queryThingsResponse, ActorRef.noSender());

            aggregatorProbe.expectMsg(
                    RetrieveThings.getBuilder(thingId1, thingId2)
                    .dittoHeaders(dittoHeaders)
                    .build());

            underTest.tell(QueryThingsPerRequestActor.Control.SERVICE_REQUESTS_DONE, getRef());

            expectMsgClass(Duration.ofSeconds(6), Done.class);
            expectTerminated(underTest);
        }};
    }

    private ActorRef createQueryThingsPerRequestActor(final QueryThings queryThings) {
        final Props props = QueryThingsPerRequestActor.props(
                queryThings,
                aggregatorProbe.ref(),
                originalSenderProbe.ref(),
                pubSubMediatorProbe.ref(),
                gatewayConfig.getHttpConfig()
        );

        return ACTOR_SYSTEM_RESOURCE.newActor(props);
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
