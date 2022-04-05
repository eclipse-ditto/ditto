/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mongodb.scala.bson.BsonInt32;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.UpdateOneModel;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.BroadcastHub;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link ThingUpdater}.
 */
public final class ThingUpdaterTest {

    private static final FiniteDuration TEN_SECOND = FiniteDuration.apply(10, "s");

    private static final SearchConfig SEARCH_CONFIG =
            DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(ConfigFactory.parseString("""
                      ditto {
                        things-search = {}
                        mongodb.uri = "mongodb://localhost:27017/test"
                      }
                    """)));

    private static final ThingId THING_ID = ThingId.of("thing:id");
    private static final long REVISION = 1234L;

    private static final String ACTOR_NAME = URLEncoder.encode(THING_ID.toString(), StandardCharsets.UTF_8);

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();
    private ActorSystem system;
    private Flow<ThingUpdater.Data, ThingUpdater.Result, NotUsed> flow;
    private TestSubscriber.Probe<ThingUpdater.Data> inletProbe;
    private TestPublisher.Probe<ThingUpdater.Result> outletProbe;

    @Before
    public void init() {
        system = actorSystemResource.getActorSystem();

        final var inletPair =
                MergeHub.of(ThingUpdater.Data.class).toMat(TestSink.probe(system), Keep.both()).run(system);
        final var outletPair =
                TestSource.<ThingUpdater.Result>probe(system).toMat(BroadcastHub.sink(), Keep.both()).run(system);

        flow = Flow.fromSinkAndSource(inletPair.first(), outletPair.second());
        inletProbe = inletPair.second();
        outletProbe = outletPair.first();
    }

    @Test
    public void recoverLastWriteModel() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props = ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG);
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the same revision arrives
            underTest.tell(AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), REVISION, null,
                    DittoHeaders.empty(), null), ActorRef.noSender());

            // THEN: No update is sent to the database during the actor's lifetime
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            inletProbe.expectNoMessage();
            underTest.tell(ReceiveTimeout.getInstance(), ActorRef.noSender());
            expectTerminated(underTest, TEN_SECOND);
            inletProbe.expectNoMessage();
        }};
    }

    @Test
    public void updateFromEvent() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props = ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG);
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the next revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);
            underTest.tell(event, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, null, null));
            assertThat(data.metadata().getTimers().size()).isEqualTo(1);
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());
        }};
    }

    @Test
    public void combineUpdatesFrom2Events() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props = ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG);

            // WHEN: 2 event of the next revisions arrive
            final var event1 = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);
            final var event2 = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(7), REVISION + 2, null,
                    DittoHeaders.empty(), null);
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));
            underTest.tell(event1, ActorRef.noSender());
            underTest.tell(event2, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 2, null, null, null));
            assertThat(data.metadata().getTimers().size()).isEqualTo(2);
            assertThat(data.metadata().getEvents().size()).isEqualTo(2);
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());

            // THEN: no other updates are sent
            underTest.tell(ReceiveTimeout.getInstance(), ActorRef.noSender());
            outletProbe.ensureSubscription();
            outletProbe.expectRequest();
            outletProbe.sendError(new IllegalStateException("Expected exception"));
            expectTerminated(underTest, TEN_SECOND);
            inletProbe.expectNoMessage();
        }};
    }

    @Test
    public void stashEventsDuringPersistence() {
        new TestKit(system) {{
            // GIVEN: an event triggers persistence
            final Props props = ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG);
            final var event1 = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);
            final var event2 = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(7), REVISION + 2, null,
                    DittoHeaders.empty(), null);
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));
            underTest.tell(event1, ActorRef.noSender());

            // WHEN: a second event arrives during persistence
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data1 = inletProbe.expectNext();
            assertThat(data1.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, null, null));
            underTest.tell(event2, ActorRef.noSender());

            // THEN: no second update is sent until the first event is persisted
            inletProbe.expectNoMessage();

            outletProbe.ensureSubscription();
            outletProbe.expectRequest();
            outletProbe.sendNext(getOKResult(REVISION + 1));
            final var data2 = inletProbe.expectNext(TEN_SECOND);
            assertThat(data2.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 2, null, null, null));
        }};
    }

    private static ThingUpdater.Result getOKResult(final long revision) {
        final var mongoWriteModel =
                MongoWriteModel.of(getThingWriteModel(revision),
                        new UpdateOneModel<>(new BsonDocument(), new BsonDocument()), true);
        return new ThingUpdater.Result(mongoWriteModel,
                WriteResultAndErrors.success(List.of(mongoWriteModel),
                        BulkWriteResult.acknowledged(0, 1, 0, 1, List.of(), List.of()), String.valueOf(revision))
        );
    }

    private static ThingWriteModel getThingWriteModel() {
        return getThingWriteModel(REVISION);
    }

    private static ThingWriteModel getThingWriteModel(final long revision) {
        final var document = new BsonDocument()
                .append("_revision", new BsonInt64(revision))
                .append("f", new BsonArray())
                .append("t", new BsonDocument().append("attributes",
                        new BsonDocument().append("x", BsonInt32.apply(5))));
        return ThingWriteModel.of(Metadata.of(THING_ID, revision, null, null, null), document);
    }
}
