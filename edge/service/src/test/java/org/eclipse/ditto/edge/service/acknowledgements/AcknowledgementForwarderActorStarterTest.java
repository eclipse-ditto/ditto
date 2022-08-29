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

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link AcknowledgementForwarderActorStarter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AcknowledgementForwarderActorStarterTest {

    private static final ThingId KNOWN_ENTITY_ID = ThingId.generateRandom();

    private static ActorSystem actorSystem;
    private static AcknowledgementConfig acknowledgementConfig;

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private TestProbe testProbe;

    @BeforeClass
    public static void setUpClass() {
        actorSystem = ActorSystem.create();
        acknowledgementConfig = DefaultAcknowledgementConfig.of(ConfigFactory.load("acknowledgement-test"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Before
    public void setUp() {
        testProbe = new TestProbe(actorSystem);
    }

    @Test
    public void getEmptyOptionalIfNoAcknowledgementsRequested() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        final AcknowledgementForwarderActorStarter underTest = getActorStarter(dittoHeaders);

        softly.assertThat(underTest.get()).isNotPresent();
    }

    @Test
    public void startForwarderActorSuccessfully() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                        AcknowledgementRequest.of(AcknowledgementLabel.of("my-ack")))
                .build();

        final AcknowledgementForwarderActorStarter underTest = getActorStarter(dittoHeaders);

        softly.assertThat(underTest.get()).isPresent();
    }

    @Test
    public void startForwarderActorWithDuplicateCorrelationId() {
        final AcknowledgementLabel customAckLabel = AcknowledgementLabel.of("my-ack");
        final String correlationId = testName.getMethodName();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                        AcknowledgementRequest.of(customAckLabel))
                .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), testProbe.ref().path().toSerializationFormat())
                .build();
        final AcknowledgementRequestDuplicateCorrelationIdException expectedException =
                AcknowledgementRequestDuplicateCorrelationIdException.newBuilder(correlationId)
                        .dittoHeaders(dittoHeaders)
                        .build();
        final Acknowledgement expectedNack = Acknowledgement.of(customAckLabel, KNOWN_ENTITY_ID,
                HttpStatus.CONFLICT, dittoHeaders, expectedException.toJson());

        final AcknowledgementForwarderActorStarter underTest = getActorStarter(dittoHeaders);

        new TestKit(actorSystem) {{
            softly.assertThat(underTest.get()).as("first start").isPresent();

            testProbe.expectNoMessage(FiniteDuration.apply(200, TimeUnit.MILLISECONDS));

            // Causes conflict in actor name as actor with same name was already started and not yet stopped.
            softly.assertThat(underTest.get()).as("second start").isNotPresent();

            final Acknowledgement firstNack = testProbe.expectMsgClass(Acknowledgement.class);

            // Ditto built-in labels got filtered out.
            expectNoMessage();

            softly.assertThat(firstNack).isEqualTo(expectedNack);
        }};
    }

    private AcknowledgementForwarderActorStarter getActorStarter(final DittoHeaders dittoHeaders) {
        final ActorRef ref = TestProbe.apply(actorSystem).ref();
        return AcknowledgementForwarderActorStarter.getInstance(actorSystem, ref, actorSystem.actorSelection(ref.path()),
                KNOWN_ENTITY_ID,
                ThingDeleted.of(KNOWN_ENTITY_ID, 1L, Instant.EPOCH, dittoHeaders, null),
                acknowledgementConfig, label -> true);
    }

}
