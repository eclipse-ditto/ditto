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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoUpdateThing;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mongodb.scala.bson.BsonInt32;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.UpdateOneModel;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.BroadcastHub;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link ThingUpdater}.
 */
public final class ThingUpdaterTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final FiniteDuration TEN_SECONDS = FiniteDuration.apply(10, "s");

    private static final SearchConfig SEARCH_CONFIG =
            DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(ConfigFactory.parseString("""
                      ditto {
                        search {
                            updater.stream.thing-deletion-timeout = 3s
                            updater.stream.write-interval = 1ms
                        }
                        mongodb.uri = "mongodb://localhost:27017/test"
                      }
                    """)));

    private static final ThingId THING_ID = ThingId.of("thing:id");
    private static final long REVISION = 1234L;

    private static final String ACTOR_NAME = getActorName(THING_ID.getName());
    public static final DittoHeaders HEADERS_WITH_ACK = DittoHeaders.newBuilder()
            .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED))
            .build();

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
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG,
                            TestProbe.apply(system).ref());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the same revision arrives
            underTest.tell(AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), REVISION, null,
                    DittoHeaders.empty(), null), ActorRef.noSender());

            // THEN: No update is sent to the database during the actor's lifetime
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            inletProbe.expectNoMessage();
            underTest.tell(ThingUpdater.ShutdownTrigger.DELETE, ActorRef.noSender());
            expectTerminated(Duration.ofSeconds(10), underTest);
            inletProbe.expectNoMessage();
        }};
    }

    @Test
    public void updateFromEvent() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the next revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);
            underTest.tell(event, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, Set.of(), null));
            assertThat(data.metadata().getTimers()).hasSize(1);
            assertThat(data.metadata().getAckRecipients()).isEmpty();
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());
        }};
    }

    @Test
    public void updateFromEventWithAck() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the next revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    HEADERS_WITH_ACK, null);
            underTest.tell(event, getRef());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, Set.of(), null));
            assertThat(data.metadata().getTimers()).hasSize(1);
            assertThat(data.metadata().getAckRecipients()).containsOnly(getSystem().actorSelection(getRef().path()));
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());
        }};
    }

    @Test
    public void updateFromOutdatedEvent() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: An event of the next revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION - 1, null,
                    DittoHeaders.empty(), null);
            underTest.tell(event, ActorRef.noSender());

            // THEN: no update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            inletProbe.expectNoMessage(Duration.ofSeconds(1));
        }};
    }

    @Test
    public void updateFromOutdatedEventWithAck() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: An event of a previous revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION - 1, null,
                    HEADERS_WITH_ACK, null);
            underTest.tell(event, getRef());

            // THEN: an update is sent for the recovered revision number 1234, which causes an empty update
            // The update is necessary to dispatch weak acknowledgements at the correct time in the presence of
            // concurrent updates.
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(getThingWriteModel().getMetadata().export());
        }};
    }

    @Test
    public void updateFromEventAfterSkippedUpdate() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: An event of the next revision arrives
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);
            underTest.tell(event, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, Set.of(), null));
            assertThat(data.metadata().getTimers()).hasSize(1);
            assertThat(data.metadata().getAckRecipients()).isEmpty();
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());

            // THEN: tell updater actor the event was skipped
            underTest.tell(Done.getInstance(), ActorRef.noSender());

            // WHEN: An event of the next revision arrives
            final var event2 = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 2, null,
                    DittoHeaders.empty(), null);
            underTest.tell(event2, ActorRef.noSender());

            // THEN: next update is processed regularly
            final var data2 = inletProbe.expectNext();
            assertThat(data2.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 2, null, Set.of(), null));
            assertThat(data2.metadata().getTimers()).hasSize(1);
            assertThat(data2.metadata().getAckRecipients()).isEmpty();
            assertThat(data2.lastWriteModel()).isEqualTo(
                    // write model was not changed because previous update was skipped
                    getThingWriteModel());
        }};
    }

    @Test
    public void handleShutdownDueToNamespacePurging() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: A shutdown command arrives
            final var shutdown =
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(THING_ID.getNamespace()),
                            DittoHeaders.empty());
            underTest.tell(shutdown, ActorRef.noSender());

            // THEN: expect actor is terminated
            expectTerminated(Duration.ofSeconds(3), underTest);
        }};
    }

    @Test
    public void handleShutdownDueToEntitiesPurging() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: A shutdown command arrives
            final List<EntityId> purgedEntities = List.of(ThingId.of("some:id"), THING_ID, ThingId.of("other:id"));
            final var shutdown =
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeEntitiesReason(purgedEntities),
                            DittoHeaders.empty());
            underTest.tell(shutdown, ActorRef.noSender());

            // THEN: expect actor is terminated
            expectTerminated(Duration.ofSeconds(3), underTest);
        }};
    }

    @Test
    public void ignoreIrrelevantShutdownDueToNamespacePurging() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: A shutdown command arrives
            final var shutdown =
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason("foo"),
                            DittoHeaders.empty());
            underTest.tell(shutdown, ActorRef.noSender());

            // THEN: expect actor is not terminated
            expectNoMessage(Duration.ofSeconds(1));
        }};
    }

    @Test
    public void ignoreIrrelevantShutdownDueToEntitiesPurging() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: A shutdown command arrives
            final List<EntityId> purgedEntities = List.of(ThingId.of("some:id"), ThingId.of("other:id"));
            final var shutdown =
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeEntitiesReason(purgedEntities),
                            DittoHeaders.empty());
            underTest.tell(shutdown, ActorRef.noSender());

            // THEN: expect actor is not terminated
            expectNoMessage(Duration.ofSeconds(1));
        }};
    }

    @Test
    public void combineUpdatesFrom2Events() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG,
                            TestProbe.apply(system).ref());

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
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 2, null, Set.of(), null));
            assertThat(data.metadata().getTimers()).hasSize(2);
            assertThat(data.metadata().getEvents()).hasSize(2);
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());

            // THEN: no other updates are sent
            underTest.tell(ThingUpdater.ShutdownTrigger.DELETE, ActorRef.noSender());
            outletProbe.ensureSubscription();
            outletProbe.expectRequest();
            outletProbe.sendError(new IllegalStateException("Expected exception"));
            expectTerminated(java.time.Duration.ofSeconds(10), underTest);
            inletProbe.expectNoMessage();
        }};
    }

    @Test
    public void stashEventsDuringPersistence() {
        new TestKit(system) {{
            // GIVEN: an event triggers persistence
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
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
            assertThat(data1.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, Set.of(), null));
            underTest.tell(event2, ActorRef.noSender());

            // THEN: no second update is sent until the first event is persisted
            inletProbe.expectNoMessage();

            outletProbe.ensureSubscription();
            outletProbe.expectRequest();
            outletProbe.sendNext(getOKResult(REVISION + 1));
            final var data2 = inletProbe.expectNext(TEN_SECONDS);
            assertThat(data2.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 2, null, Set.of(), null));
        }};
    }

    @Test
    public void policyIdChangeTriggersSync() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model without policy ID
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: A policy reference tag arrives with a policy ID
            final var policyId = PolicyId.of(THING_ID);
            final var policyTag = PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, 1L));
            underTest.tell(policyTag, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(
                    Metadata.of(THING_ID, REVISION, null, Set.of(PolicyTag.of(policyId, 1L)), null));
            assertThat(data.metadata().getUpdateReasons()).contains(UpdateReason.POLICY_UPDATE);
            assertThat(data.metadata().getTimers()).isEmpty();
        }};
    }

    @Test
    public void triggerUpdateOnCommand() {
        new TestKit(system) {{
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            final var command = SudoUpdateThing.of(THING_ID, UpdateReason.MANUAL_REINDEXING, DittoHeaders.empty());
            underTest.tell(command, ActorRef.noSender());

            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION, null, Set.of(), null));
            assertThat(data.metadata().getTimers()).isEmpty();
            assertThat(data.lastWriteModel()).isEqualTo(getThingWriteModel());
        }};
    }

    @Test
    public void forceUpdateOnCommand() {
        new TestKit(system) {{
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final var expectedMetadata = Metadata.of(THING_ID, REVISION, null, Set.of(), null);
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            final var command = SudoUpdateThing.of(THING_ID, UpdateReason.MANUAL_REINDEXING, DittoHeaders.newBuilder()
                    .putHeader("force-update", "true")
                    .build());
            underTest.tell(command, ActorRef.noSender());

            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(expectedMetadata);
            assertThat(data.metadata().getTimers()).isEmpty();
            assertThat(data.lastWriteModel()).isEqualTo(ThingDeleteModel.of(expectedMetadata));
        }};
    }

    @Test
    public void shutdownOnThingDeletedCommand() {
        new TestKit(system) {{
            // GIVEN: ThingUpdater recovers with a write model of revision 1234
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG, getTestActor());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));


            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            // WHEN: ThingDeleted of the next revision arrives
            final var event = ThingDeleted.of(THING_ID, REVISION + 1, null, DittoHeaders.empty(), null);
            underTest.tell(event, ActorRef.noSender());

            // THEN: 1 update is sent
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            final var data = inletProbe.expectNext();
            assertThat(data.metadata().export()).isEqualTo(Metadata.of(THING_ID, REVISION + 1, null, Set.of(), null));
            assertThat(data.metadata().getUpdateReasons()).contains(UpdateReason.THING_UPDATE);
            assertThat(data.metadata().getEvents()).hasOnlyElementsOfType(ThingDeleted.class);
            assertThat(data.metadata().getTimers()).hasSize(1);

            outletProbe.sendNext(getOKResult(REVISION + 1));

            // THEN: expect actor is stopped after timeout
            expectTerminated(Duration.ofSeconds(5), underTest);
        }};
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(system) {{
            final Props recoveryProps =
                    ThingUpdater.props(flow, id -> Source.never(), SEARCH_CONFIG, TestProbe.apply(system).ref());
            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG,
                            TestProbe.apply(system).ref());
            final var shutdown =
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason("thing"), DittoHeaders.empty());
            final var event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(6), REVISION + 1, null,
                    DittoHeaders.empty(), null);

            // shutdown during recovery
            final var actor1 = watch(childActorOf(recoveryProps, getActorName("1")));
            actor1.tell(shutdown, getTestActor());
            expectTerminated(Duration.ofSeconds(10), actor1);

            // shutdown when persisting
            final var actor3 = watch(childActorOf(props, getActorName("3")));
            actor3.tell(event, getTestActor());
            inletProbe.ensureSubscription();
            inletProbe.request(16);
            inletProbe.expectNext();
            actor3.tell(shutdown, getTestActor());
            expectTerminated(Duration.ofSeconds(10), actor3);

            // shutdown when retrying
            final var actor4 = watch(childActorOf(props, getActorName("4")));
            actor4.tell(event, getTestActor());
            inletProbe.expectNext();
            outletProbe.ensureSubscription();
            assertThat(outletProbe.expectRequest()).isEqualTo(16);
            outletProbe.sendError(new IllegalStateException("expected"));
            actor4.tell(shutdown, getTestActor());
            expectTerminated(Duration.ofSeconds(10), actor4);

            // shutdown when ready
            final var actor5 = watch(childActorOf(props, getActorName("5")));
            actor5.tell(event, getTestActor());
            inletProbe.expectNext();
            outletProbe.sendNext(getOKResult(REVISION + 2));
            actor5.tell(shutdown, getTestActor());
            expectTerminated(Duration.ofSeconds(10), actor5);
        }};
    }

    @Test
    public void updateSkipped() {
        new TestKit(system) {{
            // GIVEN: search mapper decides to skip updates
            final TestProbe inputProbe = TestProbe.apply(system);
            final Flow<ThingUpdater.Data, ThingUpdater.Result, NotUsed> flow = Flow.fromSinkAndSource(
                    Sink.foreach(data -> inputProbe.ref().tell(data, ActorRef.noSender())),
                    Source.empty()
            );

            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(getThingWriteModel()), SEARCH_CONFIG,
                            TestProbe.apply(system).ref());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the next revision arrives
            underTest.tell(AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), REVISION + 1, null,
                    DittoHeaders.empty(), null), ActorRef.noSender());

            // THEN: Update is triggered only once
            inputProbe.expectMsgClass(ThingUpdater.Data.class);
            inputProbe.expectNoMessage(FiniteDuration.apply(5, "s"));
        }};
    }

    @Test
    public void initialUpdateSkipped() {
        new TestKit(system) {{
            // GIVEN: search mapper decides to skip updates
            final TestProbe inputProbe = TestProbe.apply(system);
            final Flow<ThingUpdater.Data, ThingUpdater.Result, NotUsed> flow = Flow.fromSinkAndSource(
                    Sink.foreach(data -> inputProbe.ref().tell(data, ActorRef.noSender())),
                    Source.empty()
            );

            final Props props =
                    ThingUpdater.props(flow, id -> Source.single(ThingDeleteModel.of(Metadata.of(THING_ID, -1, null,
                                    Set.of(), null))), SEARCH_CONFIG,
                            TestProbe.apply(system).ref());
            final ActorRef underTest = watch(childActorOf(props, ACTOR_NAME));

            // WHEN: An event of the next revision arrives
            underTest.tell(SudoUpdateThing.of(THING_ID, UpdateReason.BACKGROUND_SYNC, DittoHeaders.empty()),
                    ActorRef.noSender());

            // THEN: Update is triggered only once
            inputProbe.expectMsgClass(ThingUpdater.Data.class);
            inputProbe.expectNoMessage(FiniteDuration.apply(5, "s"));
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
        return ThingWriteModel.of(Metadata.of(THING_ID, revision, null, Set.of(), null), document);
    }

    private static String getActorName(final String name) {
        return URLEncoder.encode(THING_ID.getNamespace() + ":" + name, Charset.defaultCharset());
    }
}
