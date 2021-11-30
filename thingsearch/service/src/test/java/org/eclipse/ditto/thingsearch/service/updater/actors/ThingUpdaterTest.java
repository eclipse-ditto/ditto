/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.ThingTag;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ThingUpdater}.
 */
public final class ThingUpdaterTest {

    private static final String NAMESPACE = "abc";

    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "myId");
    private static final PolicyId POLICY_ID = PolicyId.of(THING_ID);

    private static final long REVISION = 1L;

    private final Thing thing = ThingsModelFactory.newThingBuilder()
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .setRevision(REVISION).build();

    private ActorSystem actorSystem;
    private TestProbe pubSubTestProbe;
    private TestProbe changeQueueTestProbe;
    private AssertingThingEventObserver
            thingEventObserver;

    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        startActorSystem(config);
        thingEventObserver = AssertingThingEventObserver.get();
    }

    @After
    public void tearDownBase() {
        shutdownActorSystem();
    }

    private void startActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubTestProbe = TestProbe.apply(actorSystem);
        changeQueueTestProbe = TestProbe.apply(actorSystem);
    }

    private void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        pubSubTestProbe = null;
    }

    @Test
    public void createThing() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final ThingCreated thingCreated = ThingCreated.of(thing, 1L, Instant.now(), dittoHeaders, null);
                underTest.tell(thingCreated, getRef());

                final Metadata metadata = changeQueueTestProbe.expectMsgClass(Metadata.class);
                assertThat((CharSequence) metadata.getThingId()).isEqualTo(THING_ID);
                assertThat(metadata.getThingRevision()).isEqualTo(1L);
                assertThat(metadata.getPolicyId()).isEmpty();
                assertThat(metadata.getPolicyRevision()).contains(-1L);

                thingEventObserver.assertProcessed(thingCreated);
            }
        };
    }

    @Test
    public void thingTagWithHigherSequenceNumberTriggersSync() {
        final long revision = 7L;
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .setRevision(revision)
                .build();
        final long thingTagRevision = revision + 9L;
        final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final ThingModified thingModified =
                        ThingModified.of(currentThing, revision, Instant.now(), DittoHeaders.empty(), null);
                underTest.tell(thingModified, ActorRef.noSender());
                final Metadata metadata = changeQueueTestProbe.expectMsgClass(Metadata.class);
                assertThat((CharSequence) metadata.getThingId()).isEqualTo(THING_ID);
                assertThat(metadata.getThingRevision()).isEqualTo(revision);
                assertThat(metadata.getPolicyId()).isEmpty();
                assertThat(metadata.getPolicyRevision()).contains(-1L);

                underTest.tell(thingTag, ActorRef.noSender());
                final Metadata metadata2 = changeQueueTestProbe.expectMsgClass(Metadata.class);
                assertThat((CharSequence) metadata2.getThingId()).isEqualTo(THING_ID);
                assertThat(metadata2.getThingRevision()).isEqualTo(thingTagRevision);
                assertThat(metadata2.getPolicyId()).isEmpty();
                assertThat(metadata2.getPolicyRevision()).contains(-1L);

                thingEventObserver.assertProcessed(thingModified);
            }
        };
    }

    @Test
    public void thingTagWithLowerSequenceNumberDoesNotTriggerSync() {
        final long revision = 7L;
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .setRevision(revision)
                .build();
        final long thingTagRevision = revision - 2L;
        final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final ThingModified thingModified =
                        ThingModified.of(currentThing, revision, Instant.now(), DittoHeaders.empty(), null);
                underTest.tell(thingModified, ActorRef.noSender());
                final Metadata metadata = changeQueueTestProbe.expectMsgClass(Metadata.class);
                assertThat((CharSequence) metadata.getThingId()).isEqualTo(THING_ID);
                assertThat(metadata.getThingRevision()).isEqualTo(revision);
                assertThat(metadata.getPolicyId()).isEmpty();
                assertThat(metadata.getPolicyRevision()).contains(-1L);

                underTest.tell(thingTag, ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
                thingEventObserver.assertProcessed(thingModified);
            }
        };
    }

    @Test
    public void policyReferenceTagTriggersPolicyUpdate() {
        final long newPolicyRevision = REVISION + 2L;
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final PolicyId policyId = PolicyId.of(THING_ID);
                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, newPolicyRevision)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId, newPolicyRevision, null)
                        .withOrigin(underTest));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, REVISION)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
                thingEventObserver.assertProcessed();
            }
        };
    }

    @Test
    public void policyIdChangeTriggersSync() {
        final PolicyId policyId1 = PolicyId.of("policy", "1");
        final PolicyId policyId2 = PolicyId.of("policy", "2");

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // establish policy ID
                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId1, 99L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId1, 99L, null)
                        .withOrigin(underTest));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId2, 9L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId2, 9L, null)
                        .withOrigin(underTest));
            }
        };
    }

    @Test
    public void acknowledgesSuccessfulSync() {
        final long thingTagRevision = 7L;
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // GIVEN: a ThingTag with nonempty sender triggers synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, getRef());

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void acknowledgesSkippedSync() {
        final long thingTagRevision = 59L;
        final long outdatedRevision = 5L;

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // GIVEN: a ThingTag with nonempty sender triggered synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, getRef());
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
                changeQueueTestProbe.expectMsgClass(Metadata.class);

                // WHEN: updater receives outdated ThingTag
                final ThingTag outdatedThingTag = ThingTag.of(THING_ID, outdatedRevision);
                underTest.tell(outdatedThingTag, getRef());

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(outdatedThingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = watch(createThingUpdaterActor());

                final DistributedPubSubMediator.Subscribe subscribe =
                        DistPubSubAccess.subscribe(Shutdown.TYPE, underTest);
                pubSubTestProbe.expectMsg(subscribe);
                pubSubTestProbe.reply(new DistributedPubSubMediator.SubscribeAck(subscribe));

                underTest.tell(Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(NAMESPACE),
                        DittoHeaders.empty()), pubSubTestProbe.ref());
                expectTerminated(underTest);
            }
        };

    }

    @Test
    public void recoverLastWriteModel() {
        new TestKit(actorSystem) {{
            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(),
                            AssertingThingEventObserver.get(), 0.0, false, true));
            final var underTest = childActorOf(props, THING_ID.toString());

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1234))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, -1, null, null, null), document);

            // GIVEN: updater is recovered with a write model
            underTest.tell(writeModel, ActorRef.noSender());

            // WHEN: updater is requested to compute incremental update against the same write model
            underTest.tell(writeModel, getRef());

            // THEN: expect no update.
            expectMsg(Done.done());
        }};
    }

    private ActorRef createThingUpdaterActor() {
        final Props props = Props.create(ThingUpdater.class,
                () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), thingEventObserver,
                        0.0, false, false));
        return actorSystem.actorOf(props, THING_ID.toString());
    }

    private static class AssertingThingEventObserver extends ThingEventObserver {

        private final List<ThingEvent<?>> processed = new ArrayList<>();

        public static AssertingThingEventObserver get() {
            return new AssertingThingEventObserver();
        }

        @Override
        public void processThingEvent(final ThingEvent<?> event) {
            processed.add(event);
        }

        private void assertProcessed(final ThingEvent<?>... expected) {
            assertThat(processed).isEqualTo(List.of(expected));
        }
    }
}
