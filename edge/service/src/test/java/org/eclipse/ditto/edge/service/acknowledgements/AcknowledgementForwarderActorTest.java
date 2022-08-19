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
package org.eclipse.ditto.edge.service.acknowledgements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link AcknowledgementForwarderActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AcknowledgementForwarderActorTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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
    public void tryToDetermineActorNameFromNullHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> AcknowledgementForwarderActor.determineActorName(null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void determineActorNameReturnsExpected() {
        final String correlationId = "my-correlation-id";
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final String expected = AcknowledgementForwarderActor.ACTOR_NAME_PREFIX + correlationId;

        final String actual = AcknowledgementForwarderActor.determineActorName(dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToDetermineActorNameWithMissingCorrelationId() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("my-requested-ack")))
                .build();

        assertThatExceptionOfType(AcknowledgementCorrelationIdMissingException.class)
                .isThrownBy(() -> AcknowledgementForwarderActor.determineActorName(dittoHeaders));
    }

    @Test
    public void createAcknowledgementForwarderAndThreadAcknowledgementThrough()
            throws ExecutionException, InterruptedException {
        new TestKit(actorSystem) {{

            final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("my-requested-ack");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .randomCorrelationId()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .build();
            final ThingId entityId = ThingId.generateRandom();
            final Signal<?> signal = ThingDeleted.of(entityId, 1L, Instant.EPOCH, dittoHeaders, null);
            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, entityId, HttpStatus.ACCEPTED, dittoHeaders);

            final Optional<ActorRef> underTest =
                    AcknowledgementForwarderActor.startAcknowledgementForwarderForTest(actorSystem,
                            getRef(), actorSystem.actorSelection(getRef().path()), entityId, signal,
                            acknowledgementConfig);

            softly.assertThat(underTest).isPresent();

            final ActorSelection ackForwarderSelection = actorSystem.actorSelection(
                    "/user/" + AcknowledgementForwarderActor.determineActorName(dittoHeaders));

            softly.assertThat(underTest.get()).isEqualByComparingTo(
                    ackForwarderSelection.resolveOne(Duration.ofMillis(100)).toCompletableFuture().get());

            ackForwarderSelection.tell(acknowledgement, getRef());
            expectMsg(acknowledgement);
        }};
    }

}
