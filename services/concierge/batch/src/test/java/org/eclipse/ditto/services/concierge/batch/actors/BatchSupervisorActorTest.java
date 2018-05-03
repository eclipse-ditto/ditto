/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.batch.actors;

import static akka.testkit.JavaTestKit.shutdownActorSystem;
import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;

import java.util.Arrays;
import java.util.UUID;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.concierge.ConciergeWrapper;
import org.eclipse.ditto.services.utils.akka.JavaTestProbe;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatch;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatchResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.base.assertions.EventAssertions;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.eclipse.ditto.signals.events.batch.BatchExecutionStarted;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Unit test for {@link BatchSupervisorActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class BatchSupervisorActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");
    public static final String THING1_ID = "com.bosch.iot.things.test:thing1";
    public static final String THING2_ID = "com.bosch.iot.things.test:thing2";

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;
    private static ActorRef underTest;
    private static ActorRef conciergeForwarder;

    @BeforeClass
    public static void setUpActorSystem() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        final ActorRef sharedRegionProxy = actorSystem.actorOf(Props.create(SharedRegionProxyMock.class));
        conciergeForwarder = actorSystem.actorOf(ConciergeForwarderActorMock.props(sharedRegionProxy),
                ConciergeForwarderActorMock.ACTOR_NAME);
        underTest = createBatchSupervisorActor();
    }

    /** */
    @AfterClass
    public static void tearDownActorSystem() {
        shutdownActorSystem(actorSystem);
    }

    /** */
    @Test
    public void batchExecutionWorksAsExpected() {
        new JavaTestProbe(actorSystem) {
            {
                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                final String batchId = randomBatchId();
                final ExecuteBatch executeBatch =
                        ExecuteBatch.of(batchId, Arrays.asList(
                                ModifyThing.of(THING1_ID,
                                        Thing.newBuilder().setId(THING1_ID).build(),
                                        null,
                                        DittoHeaders.newBuilder().build()),
                                ModifyThing.of(THING2_ID,
                                        Thing.newBuilder().setId(THING2_ID).build(),
                                        null,
                                        DittoHeaders.newBuilder().build())),
                                DittoHeaders.newBuilder().correlationId(batchId).build());

                underTest.tell(executeBatch, ref());

                expectMsgAllClass()
                        .of(ExecuteBatchResponse.class, executeBatchResponse ->
                                assertThat(executeBatchResponse).hasCorrelationId(batchId))
                        .of(BatchExecutionFinished.class, batchExecutionFinished ->
                                EventAssertions.assertThat(batchExecutionFinished).hasCorrelationId(batchId))
                        .run();
            }
        };
    }

    /** */
    @Test
    public void batchExecutionResumesAfterRecovery() throws InterruptedException {
        new JavaTestProbe(actorSystem) {
            {
                subscribeToEvents(this, BatchExecutionStarted.TYPE, BatchExecutionFinished.TYPE);

                // batch1
                final String batchId1 = randomBatchId();
                final ExecuteBatch executeBatch1 =
                        ExecuteBatch.of(batchId1, Arrays.asList(
                                ModifyThing.of(THING1_ID,
                                        Thing.newBuilder().setId(THING1_ID).build(),
                                        null,
                                        DittoHeaders.newBuilder().build()),
                                ModifyThing.of(THING2_ID,
                                        Thing.newBuilder().setId(THING2_ID).build(),
                                        null,
                                        DittoHeaders.newBuilder().build())),
                                DittoHeaders.newBuilder().correlationId(batchId1).build());

                underTest.tell(executeBatch1, ref());

                final ExecuteBatchResponse executeBatchResponse1 = expectMsgClass(ExecuteBatchResponse.class);
                assertThat(executeBatchResponse1).hasCorrelationId(batchId1);

                final BatchExecutionStarted batchExecutionStarted1 = expectMsgClass(BatchExecutionStarted.class);
                EventAssertions.assertThat(batchExecutionStarted1).hasCorrelationId(batchId1);

                final BatchExecutionFinished batchExecutionFinished1 = expectMsgClass(BatchExecutionFinished.class);
                EventAssertions.assertThat(batchExecutionFinished1).hasCorrelationId(batchId1);

                // batch2
                final String batchId2 = randomBatchId();
                final ExecuteBatch executeBatch2 =
                        ExecuteBatch.of(batchId2, Arrays.asList(
                                ModifyFeature.of(
                                        THING1_ID,
                                        Feature.newBuilder().withId("fluxCompensator").build(),
                                        DittoHeaders.newBuilder().build()),
                                ModifyFeature.of(
                                        THING2_ID,
                                        Feature.newBuilder().withId("fluxCompensator").build(),
                                        DittoHeaders.newBuilder().build())),
                                DittoHeaders.newBuilder().correlationId(batchId2).build());

                underTest.tell(executeBatch2, ref());

                final ExecuteBatchResponse executeBatchResponse2 = expectMsgClass(ExecuteBatchResponse.class);
                assertThat(executeBatchResponse2).hasCorrelationId(batchId2);

                final BatchExecutionStarted batchExecutionStarted2 = expectMsgClass(BatchExecutionStarted.class);
                EventAssertions.assertThat(batchExecutionStarted2).hasCorrelationId(batchId2);

                // terminate and restart - only batch2 must resume execution and deliver an event
                terminate(this, underTest);

                // sometimes the actor name is not unique, wait a bit
                Thread.sleep(500);

                underTest = createBatchSupervisorActor();

                final BatchExecutionFinished batchExecutionFinished2 = expectMsgClass(BatchExecutionFinished.class);
                EventAssertions.assertThat(batchExecutionFinished2).hasCorrelationId(batchId2);

                expectNoMsg();
            }
        };
    }

    private static ActorRef createBatchSupervisorActor() {
        final String name = BatchSupervisorActor.ACTOR_NAME;
        return actorSystem.actorOf(BatchSupervisorActor.props(pubSubMediator, conciergeForwarder), name);
    }

    private static void terminate(final JavaTestProbe javaTestProbe, final ActorRef underTest) {
        javaTestProbe.watch(underTest);
        actorSystem.stop(underTest);
        javaTestProbe.expectTerminated(underTest);
    }

    private static String randomBatchId() {
        return "BatchSupervisorActorTest-" + UUID.randomUUID().toString();
    }

    private static void subscribeToEvents(final JavaTestProbe javaTestProbe, final String... events) {
        final String group = "BatchSupervisorActorTest" + UUID.randomUUID().toString();
        for (final String e : events) {
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(e, group, javaTestProbe.ref()),
                    javaTestProbe.ref());
            javaTestProbe.expectMsgClass(DistributedPubSubMediator.SubscribeAck.class);
        }
    }

    private static final class SharedRegionProxyMock extends AbstractActor {

        private int modifyFeatureCount = 0;
        private final JsonParsableRegistry<? extends Command> commandRegistry = ThingCommandRegistry.newInstance();

        SharedRegionProxyMock() {
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(ShardedMessageEnvelope.class, this::extractCommandFromEnvelop)
                    .matchAny(this::unhandled)
                    .build();
        }

        private void extractCommandFromEnvelop(ShardedMessageEnvelope shardedMessageEnvelope) {
            final String type = shardedMessageEnvelope.getType();
            final JsonObject jsonCommand = shardedMessageEnvelope.getMessage();
            final DittoHeaders dittoHeaders = shardedMessageEnvelope.getDittoHeaders();

            if (type.equals(ModifyThing.TYPE)) {
                final ModifyThing command = (ModifyThing) commandRegistry.parse(jsonCommand, dittoHeaders);

                getSender().tell(ModifyThingResponse.modified(command.getId(),
                        command.getDittoHeaders()), getSelf());
            }
            else if (type.equals(ModifyFeature.TYPE)) {
                final ModifyFeature command = (ModifyFeature) commandRegistry.parse(jsonCommand, dittoHeaders);

                if (command.getDittoHeaders().isDryRun()) {
                    respondToModifyFeature(command);
                } else {
                    modifyFeatureCount++;
                    if (modifyFeatureCount > 2) {
                        respondToModifyFeature(command);
                    }
                }
            }
        }

        private void respondToModifyFeature(final ModifyFeature command) {
            final ModifyFeatureResponse response = ModifyFeatureResponse.modified(command.getId(), command.getFeatureId(),
                    command.getDittoHeaders());
            getSender().tell(response, getSelf());
        }
    }

    public static final class ConciergeForwarderActorMock extends AbstractActor {

        public static final String ACTOR_NAME = "conciergeForwarder";

        private final ActorRef enforcerShardRegion;

        private ConciergeForwarderActorMock(final ActorRef enforcerShardRegion) {
            this.enforcerShardRegion = enforcerShardRegion;
        }


        /**
         * Creates Akka configuration object Props for this actor.
         *
         * @param enforcerShardRegion the ActorRef of the enforcerShardRegion.
         * @return the Akka configuration Props object.
         */
        public static Props props(final ActorRef enforcerShardRegion) {

            return Props.create(ConciergeForwarderActorMock.class,
                    () -> new ConciergeForwarderActorMock(enforcerShardRegion));
        }

        @Override
        public AbstractActor.Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(Signal.class, signal -> {
                        final ShardedMessageEnvelope msg = ConciergeWrapper.wrapForEnforcer(signal);
                        enforcerShardRegion.tell(msg, getSender());
                    })
                    .build();
        }
    }

}
