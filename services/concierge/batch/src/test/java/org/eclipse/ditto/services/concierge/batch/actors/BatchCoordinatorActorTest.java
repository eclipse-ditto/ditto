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

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.akka.JavaTestProbe;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatch;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatchResponse;
import org.eclipse.ditto.signals.commands.batch.exceptions.BatchAlreadyExecutingException;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.base.assertions.EventAssertions;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Kill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Unit test for {@link BatchCoordinatorActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class BatchCoordinatorActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");

    private static final String FEATURE_ID_1 = "feature1";
    private static final String FEATURE_ID_2 = "feature2";
    private static final String FEATURE_ID_3 = "feature3";
    public static final String THING1_ID = "com.bosch.iot.things.test:thing1";
    public static final String THING2_ID = "com.bosch.iot.things.test:thing2";

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;
    private static ActorRef conciergeForwarder;

    @BeforeClass
    public static void setUpActorSystem() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        final ActorRef sharedRegionProxy = actorSystem.actorOf(Props.create(SharedRegionProxyMock.class));
        conciergeForwarder = actorSystem.actorOf(BatchSupervisorActorTest.ConciergeForwarderActorMock.props
                        (sharedRegionProxy), BatchSupervisorActorTest.ConciergeForwarderActorMock.ACTOR_NAME);
    }

    /** */
    @AfterClass
    public static void tearDownActorSystem() {
        shutdownActorSystem(actorSystem);
    }

    /** */
    @Test
    public void batchFinishedWithCompleteSuccess() {
        new JavaTestProbe(actorSystem) {
            {
                final String batchId = randomBatchId();
                final ModifyThing modifyThing1 = ModifyThing.of(THING1_ID,
                        Thing.newBuilder().setId(THING1_ID).build(),
                        null,
                        DittoHeaders.newBuilder()
                                .correlationId(UUID.randomUUID().toString())
                                .build());
                final ModifyThing modifyThing2 = ModifyThing.of(THING2_ID,
                        Thing.newBuilder().setId(THING2_ID).build(),
                        null,
                        DittoHeaders.newBuilder()
                                .correlationId(UUID.randomUUID().toString())
                                .build());
                final ExecuteBatch executeBatch =
                        ExecuteBatch.of(batchId, Arrays.asList(modifyThing1, modifyThing2),
                                DittoHeaders.newBuilder().correlationId(batchId).build());

                final ActorRef underTest = createBatchCoordinatorActor(batchId);

                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                underTest.tell(executeBatch, ref());

                expectMsgAllClass()
                        .of(ExecuteBatchResponse.class,
                                executeBatchResponse -> assertThat(executeBatchResponse).hasCorrelationId(batchId))
                        .of(BatchExecutionFinished.class, batchExecutionFinished -> {
                            EventAssertions.assertThat(batchExecutionFinished).hasCorrelationId(batchId);
                            Assertions.assertThat(batchExecutionFinished.getCommandResponses())
                                    .containsExactlyInAnyOrder(
                                            ModifyThingResponse.modified(modifyThing1.getId(),
                                                    modifyThing1.getDittoHeaders()),
                                            ModifyThingResponse.modified(modifyThing2.getId(),
                                                    modifyThing2.getDittoHeaders()));
                        })
                        .run();
            }
        };
    }

    /** */
    @Test
    public void batchFinishedWithPartialSuccess() {
        new JavaTestProbe(actorSystem) {
            {
                final String batchId = randomBatchId();
                final String thingId = "com.bosch.iot.things.test:thing";
                final String correlationIdModifyFeature2 = UUID.randomUUID().toString();
                final String correlationIdModifyFeature3 = UUID.randomUUID().toString();

                final ExecuteBatch executeBatch =
                        ExecuteBatch.of(batchId, Arrays.asList(
                                ModifyFeature.of(
                                        thingId,
                                        Feature.newBuilder().withId(FEATURE_ID_2).build(),
                                        DittoHeaders.newBuilder()
                                                .correlationId(correlationIdModifyFeature2)
                                                .build()),
                                ModifyFeature.of(
                                        thingId,
                                        Feature.newBuilder().withId(FEATURE_ID_3).build(),
                                        DittoHeaders.newBuilder()
                                                .correlationId(correlationIdModifyFeature3)
                                                .build())),
                                DittoHeaders.newBuilder().correlationId(batchId).build());

                final ActorRef underTest = createBatchCoordinatorActor(batchId);

                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                underTest.tell(executeBatch, ref());

                expectMsgAllClass()
                        .of(ExecuteBatchResponse.class,
                                executeBatchResponse -> assertThat(executeBatchResponse).hasCorrelationId(batchId))
                        .of(BatchExecutionFinished.class, batchExecutionFinished -> {
                            EventAssertions.assertThat(batchExecutionFinished).hasCorrelationId(batchId);
                            Assertions.assertThat(batchExecutionFinished.getCommandResponses()).contains(
                                    ThingErrorResponse.of(
                                            FeatureNotModifiableException.newBuilder(thingId, FEATURE_ID_2)
                                                    .dittoHeaders(DittoHeaders.newBuilder()
                                                            .correlationId(correlationIdModifyFeature2)
                                                            .build())
                                                    .build()));
                            Assertions.assertThat(batchExecutionFinished.getCommandResponses()).contains(
                                    ModifyFeatureResponse.modified(thingId, FEATURE_ID_3,
                                            DittoHeaders.newBuilder()
                                                    .correlationId(correlationIdModifyFeature3)
                                                    .build()));
                        })
                        .run();
            }
        };
    }

    /** */
    @Test
    public void batchFailsDryRun() {
        new JavaTestProbe(actorSystem) {
            {
                final String batchId = randomBatchId();
                final String thingId = "com.bosch.iot.things.test:thing";

                final ExecuteBatch executeBatch =
                        ExecuteBatch.of(batchId, Arrays.asList(
                                ModifyFeature.of(
                                        thingId,
                                        Feature.newBuilder().withId(FEATURE_ID_1).build(),
                                        DittoHeaders.newBuilder().build()),
                                ModifyFeature.of(
                                        thingId,
                                        Feature.newBuilder().withId(FEATURE_ID_3).build(),
                                        DittoHeaders.newBuilder().build())),
                                DittoHeaders.newBuilder().correlationId(batchId).build());

                final ActorRef underTest = createBatchCoordinatorActor(batchId);

                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                underTest.tell(executeBatch, ref());

                expectMsgClass(ThingErrorResponse.class, thingErrorResponse ->
                        assertThat(thingErrorResponse).hasCorrelationId(batchId));

                expectNoMsg();
            }
        };
    }

    /** */
    @Test
    public void batchExecutesOnlyOnce() {
        new JavaTestProbe(actorSystem) {
            {
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

                final ActorRef underTest = createBatchCoordinatorActor(batchId);

                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                underTest.tell(executeBatch, ref());
                underTest.tell(executeBatch, ref());

                expectMsgAllClass()
                        .of(ThingErrorResponse.class, thingErrorResponse -> assertThat(thingErrorResponse)
                                .hasCorrelationId(batchId)
                                .dittoRuntimeExceptionHasErrorCode(BatchAlreadyExecutingException.ERROR_CODE))
                        .of(ExecuteBatchResponse.class,
                                executeBatchResponse -> assertThat(executeBatchResponse).hasCorrelationId(batchId))
                        .of(BatchExecutionFinished.class,
                                batchExecutionFinished -> EventAssertions.assertThat(batchExecutionFinished)
                                        .hasCorrelationId(batchId))
                        .run();
            }
        };
    }

    /** */
    @Test
    public void batchExecutionResumesAfterRecovery() {
        new JavaTestProbe(actorSystem) {
            {
                final String batchId = randomBatchId();
                final ExecuteBatch executeBatch =
                        ExecuteBatch.of(batchId, Arrays.asList(
                                ModifyAttributes.of(
                                        THING1_ID,
                                        Attributes.newBuilder().build(),
                                        DittoHeaders.newBuilder().build()),
                                ModifyAttributes.of(
                                        THING2_ID,
                                        Attributes.newBuilder().build(),
                                        DittoHeaders.newBuilder().build())),
                                DittoHeaders.newBuilder().correlationId(batchId).build());

                final ActorRef underTest = createBatchCoordinatorActor(batchId);

                subscribeToEvents(this, BatchExecutionFinished.TYPE);

                underTest.tell(executeBatch, ref());

                final ExecuteBatchResponse executeBatchResponse = expectMsgClass(ExecuteBatchResponse.class);
                assertThat(executeBatchResponse).hasCorrelationId(batchId);

                watch(underTest);
                underTest.tell(Kill.getInstance(), ref());
                expectTerminated(underTest);

                Retry.untilSuccess(() -> createBatchCoordinatorActor(batchId));

                final BatchExecutionFinished batchExecutionFinished = expectMsgClass(BatchExecutionFinished.class);
                EventAssertions.assertThat(batchExecutionFinished).hasCorrelationId(batchId);
            }
        };
    }

    private static ActorRef createBatchCoordinatorActor(final String batchId) {
        final Props props = BatchCoordinatorActor.props(batchId, pubSubMediator, conciergeForwarder);
        final String name = BatchCoordinatorActor.ACTOR_NAME_PREFIX + batchId;

        return actorSystem.actorOf(props, name);
    }

    private static String randomBatchId() {
        return "BatchCoordinatorActorTest-" + UUID.randomUUID().toString();
    }

    private void subscribeToEvents(final JavaTestProbe javaTestProbe, final String... events) {
        final String group = "BatchCoordinatorActorTest" + UUID.randomUUID().toString();
        for (final String e : events) {
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(e, group, javaTestProbe.ref()),
                    javaTestProbe.ref());
        }
        final JavaTestProbe.ExpectMsgAllClass consumeSubscribeAcks = javaTestProbe.expectMsgAllClass();
        for (final String e : events) {
            consumeSubscribeAcks.of(DistributedPubSubMediator.SubscribeAck.class, subscribeAck -> {});
        }
        consumeSubscribeAcks.run();
    }

    private static final class SharedRegionProxyMock extends AbstractActor {

        private int modifyAttributesCount = 0;
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

                final String thingId = command.getThingId();
                final String featureId = command.getFeatureId();
                if (command.getDittoHeaders().isDryRun()) {
                    if (featureId.equals(FEATURE_ID_1)) {
                        getSender().tell(FeatureNotModifiableException.newBuilder(thingId, featureId)
                                        .dittoHeaders(command.getDittoHeaders())
                                        .build(),
                                getSelf());
                    } else {
                        getSender().tell(ModifyFeatureResponse.modified(thingId, featureId,
                                command.getDittoHeaders()), getSelf());
                    }
                } else {
                    if (featureId.equals(FEATURE_ID_2)) {
                        getSender().tell(FeatureNotModifiableException.newBuilder(thingId, featureId)
                                        .dittoHeaders(command.getDittoHeaders())
                                        .build(),
                                getSelf());
                    } else {
                        getSender().tell(ModifyFeatureResponse.modified(thingId, featureId,
                                command.getDittoHeaders()), getSelf());
                    }
                }

            } else if (type.equals(ModifyAttributes.TYPE)) {
                final ModifyAttributes command = (ModifyAttributes) commandRegistry.parse(jsonCommand, dittoHeaders);

                if (command.getDittoHeaders().isDryRun()) {
                    respondToModifyAttributes(command);
                } else {
                    modifyAttributesCount++;
                    if (modifyAttributesCount > 2) {
                        respondToModifyAttributes(command);
                    }
                }
            }
        }

        private void respondToModifyAttributes(final ModifyAttributes command) {
            final ModifyAttributesResponse response =
                    ModifyAttributesResponse.modified(command.getThingId(), command.getDittoHeaders());
            getSender().tell(response, getSelf());
        }
    }

}
