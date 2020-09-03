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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
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

    @Mock
    private ActorContext actorContext;

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

        when(actorContext.actorOf(any(Props.class), anyString()))
                .thenAnswer((Answer<ActorRef>) invocationOnMock -> actorSystem.actorOf(invocationOnMock.getArgument(0),
                        invocationOnMock.getArgument(1)));
        when(actorContext.sender()).thenReturn(testProbe.ref());
    }

    @Test
    public void getEmptyOptionalIfNoAcknowledgementsRequested() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        final AcknowledgementForwarderActorStarter underTest = getActorStarter(dittoHeaders);

        softly.assertThat(underTest.get()).isNotPresent();
        Mockito.verifyNoInteractions(actorContext);
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
                .build();
        final AcknowledgementRequestDuplicateCorrelationIdException expectedException =
                AcknowledgementRequestDuplicateCorrelationIdException.newBuilder(correlationId)
                        .dittoHeaders(dittoHeaders)
                        .build();
        final Acknowledgement expectedNack = Acknowledgement.of(customAckLabel, KNOWN_ENTITY_ID,
                HttpStatusCode.CONFLICT, dittoHeaders, expectedException.toJson());

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
        return AcknowledgementForwarderActorStarter.getInstance(actorContext, KNOWN_ENTITY_ID,
                ThingDeleted.of(KNOWN_ENTITY_ID, 1L, dittoHeaders),
                acknowledgementConfig);
    }

}
