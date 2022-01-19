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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.model.ReplaceOneModel;
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

    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        startActorSystem(config);
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
                Assertions.assertThat((CharSequence) metadata.getThingId()).isEqualTo(THING_ID);
                Assertions.assertThat(metadata.getThingRevision()).isEqualTo(1L);
                Assertions.assertThat(metadata.getPolicyId()).isEmpty();
                Assertions.assertThat(metadata.getPolicyRevision()).contains(-1L);
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
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId, REVISION)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
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
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policyId2, 9L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policyId2, 9L, null)
                        .withOrigin(underTest).withUpdateReason(UpdateReason.POLICY_UPDATE));
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
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            Duration.ofMinutes(1), 0.0, false, true));
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

    @Test
    public void forceUpdateAfterInitialStart() throws InterruptedException {
        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.of(THING_ID);
            final Duration forceUpdateAfterStartTimeout = Duration.ofSeconds(1);

            final Props props = Props.create(ThingUpdater.class,
                    () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0,
                            forceUpdateAfterStartTimeout, 0.0, false, true));
            final var underTest = childActorOf(props, THING_ID.toString());

            final var document = new BsonDocument()
                    .append("_revision", new BsonInt64(1234))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel = ThingWriteModel.of(Metadata.of(THING_ID, 1234L, policyId, 1L, null), document);

            // GIVEN: updater is recovered with a write model
            underTest.tell(writeModel, ActorRef.noSender());

            TimeUnit.SECONDS.sleep(forceUpdateAfterStartTimeout.multipliedBy(2).toSeconds());

            final var document2 = new BsonDocument()
                    .append("_revision", new BsonInt64(1235))
                    .append("d", new BsonArray())
                    .append("s", new BsonDocument().append("Lorem ipsum", new BsonString(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                                    "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                    )));
            final var writeModel2 = ThingWriteModel.of(Metadata.of(THING_ID, 1235L, policyId, 1L, null), document2);


            // WHEN: updater is requested to compute incremental update against the same write model
            underTest.tell(writeModel2, getRef());

            // THEN: expect full forced update
            final ReplaceOneModel<?> replaceOneModel = expectMsgClass(ReplaceOneModel.class);
            Assertions.assertThat(replaceOneModel.getReplacement()).isEqualTo(document2);
        }};
    }

    private ActorRef createThingUpdaterActor() {
        final Props props = Props.create(ThingUpdater.class,
                () -> new ThingUpdater(pubSubTestProbe.ref(), changeQueueTestProbe.ref(), 0.0, Duration.ofMinutes(1),
                        0.0, false, false));
        return actorSystem.actorOf(props, THING_ID.toString());
    }

}
