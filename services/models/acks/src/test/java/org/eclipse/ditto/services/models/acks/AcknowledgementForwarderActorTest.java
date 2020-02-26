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
package org.eclipse.ditto.services.models.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link AcknowledgementForwarderActor}.
 */
public final class AcknowledgementForwarderActorTest {

    private ActorSystem actorSystem;
    private AcknowledgementConfig acknowledgementConfig;

    @Before
    public void setUp() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        acknowledgementConfig = DefaultAcknowledgementConfig.of(ConfigFactory.load("acknowledgement-test"));
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    @Test
    public void createAcknowledgementForwarderWithMissingCorrelationId() {
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("my-requested-ack");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                .build();

        assertThatExceptionOfType(AcknowledgementCorrelationIdMissingException.class).isThrownBy(() ->
                AcknowledgementForwarderActor.determineActorName(dittoHeaders)
        );
    }

    @Test
    public void createAcknowledgementForwarderAndThreadAcknowledgementThrough()
            throws ExecutionException, InterruptedException {
        final String correlationId = UUID.randomUUID().toString();
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("my-requested-ack");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                .build();
        final EntityId entityId = DefaultEntityId.of("foo:bar");
        final Acknowledgement acknowledgement =
                Acknowledgement.of(acknowledgementLabel, entityId, HttpStatusCode.ACCEPTED, dittoHeaders);

        new TestKit(actorSystem) {
            {
                final Optional<ActorRef> actorRef = AcknowledgementForwarderActor.startAcknowledgementForwarder(
                        actorSystem, getRef(), getRef(), entityId, dittoHeaders, acknowledgementConfig);
                assertThat(actorRef).isPresent();

                final ActorSelection ackForwarderSelection = actorSystem.actorSelection(
                        "/user/" + AcknowledgementForwarderActor.determineActorName(dittoHeaders));
                assertThat(actorRef.get()).isEqualByComparingTo(
                        ackForwarderSelection.resolveOne(Duration.ofMillis(100)).toCompletableFuture().get());

                ackForwarderSelection.tell(acknowledgement, getRef());
                expectMsg(acknowledgement);
            }
        };
    }

    @Test
    public void createAcknowledgementForwarderWithDuplicatedCorrelationId() {
        final String correlationId = UUID.randomUUID().toString();
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("my-requested-ack");
        final AcknowledgementLabel acknowledgementLabel2 = AcknowledgementLabel.of("my-requested-ack-2");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                .build();
        final DittoHeaders dittoHeaders2 = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel2))
                .build();
        final EntityId entityId = DefaultEntityId.of("foo:bar");
        final AcknowledgementRequestDuplicateCorrelationIdException expectedException =
                AcknowledgementRequestDuplicateCorrelationIdException.newBuilder(correlationId)
                        .dittoHeaders(dittoHeaders2)
                        .build();

        new TestKit(actorSystem) {
            {
                final Optional<ActorRef> actorRef = AcknowledgementForwarderActor.startAcknowledgementForwarder(
                        actorSystem, getRef(), getRef(), entityId, dittoHeaders, acknowledgementConfig);
                assertThat(actorRef).isPresent();
                expectNoMessage(Duration.ofMillis(200));

                final Optional<ActorRef> actorRef2 = AcknowledgementForwarderActor.startAcknowledgementForwarder(
                        actorSystem, getRef(), getRef(), entityId, dittoHeaders2, acknowledgementConfig);
                assertThat(actorRef2).isNotPresent();

                final Acknowledgement nack = expectMsgClass(Acknowledgement.class);
                assertThat(nack.getStatusCode()).isEqualByComparingTo(HttpStatusCode.CONFLICT);
                assertThat((CharSequence) nack.getEntityId()).isEqualTo(entityId);
                assertThat(nack.getEntity()).contains(expectedException.toJson());
            }
        };
    }

}