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

import java.util.function.Consumer;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.models.acks.AcknowledgementAggregatorActor}.
 */
public final class AcknowledgementAggregatorActorTest {

    private ActorSystem actorSystem;
    private AcknowledgementConfig config;
    private HeaderTranslator headerTranslator;

    @Before
    public void init() {
        actorSystem = ActorSystem.create();
        config = DefaultAcknowledgementConfig.of(ConfigFactory.empty());
        headerTranslator = HeaderTranslator.of();
    }

    @After
    public void cleanUp() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void keepCommandHeaders() {
        new TestKit(actorSystem) {{
            // GIVEN
            final String tag = "tag";
            final String correlationId = "keepCommandHeaders";
            final ThingId thingId = ThingId.of("thing:id");
            final AcknowledgementLabel label1 = AcknowledgementLabel.of("ack1");
            final AcknowledgementLabel label2 = AcknowledgementLabel.of("ack2");
            final ThingModifyCommand<?> command = DeleteThing.of(thingId, DittoHeaders.newBuilder()
                    .correlationId(correlationId)
                    .acknowledgementRequest(AcknowledgementRequest.of(label1), AcknowledgementRequest.of(label2))
                    .putHeader(tag, DeleteThing.class.getSimpleName())
                    .build());
            final ActorRef underTest = childActorOf(getAcknowledgementAggregatorProps(command, this));

            // WHEN
            final Acknowledgement ack1 = Acknowledgement.of(label1, thingId, HttpStatusCode.UNAUTHORIZED,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label1.toString()).build());
            final Acknowledgement ack2 = Acknowledgement.of(label2, thingId, HttpStatusCode.PAYMENT_REQUIRED,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label2.toString()).build());
            underTest.tell(ack1, ActorRef.noSender());
            underTest.tell(ack2, ActorRef.noSender());

            // THEN
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            assertThat(acks.getDittoHeaders()).isEqualTo(command.getDittoHeaders());
            assertThat(acks.getSize()).isEqualTo(2);
        }};
    }

    @Test
    public void discardDuplicateAndUnsolicitedAcknowledgements() {
        new TestKit(actorSystem) {{
            // GIVEN
            final String tag = "tag";
            final String correlationId = "discardDuplicateAndUnsolicitedAcknowledgements";
            final ThingId thingId = ThingId.of("thing:id");
            final AcknowledgementLabel label1 = AcknowledgementLabel.of("ack1");
            final AcknowledgementLabel label2 = AcknowledgementLabel.of("ack2");
            final AcknowledgementLabel label3 = AcknowledgementLabel.of("ack3");
            final ThingModifyCommand<?> command = DeleteThing.of(thingId, DittoHeaders.newBuilder()
                    .correlationId(correlationId)
                    .acknowledgementRequest(AcknowledgementRequest.of(label1), AcknowledgementRequest.of(label2))
                    .putHeader(tag, DeleteThing.class.getSimpleName())
                    .build());
            final ActorRef underTest = childActorOf(getAcknowledgementAggregatorProps(command, this));

            // WHEN
            final Acknowledgement ack1 = Acknowledgement.of(label1, thingId, HttpStatusCode.UNAUTHORIZED,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label1.toString()).build());
            final Acknowledgement ack2 = Acknowledgement.of(label2, thingId, HttpStatusCode.PAYMENT_REQUIRED,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label2.toString()).build());
            final Acknowledgement ack3 = Acknowledgement.of(label3, thingId, HttpStatusCode.OK,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, "unsolicited").build());
            final Acknowledgement ack4 = Acknowledgement.of(label1, thingId, HttpStatusCode.UNAUTHORIZED,
                    DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, "duplicate").build());
            underTest.tell(ack1, ActorRef.noSender());
            underTest.tell(ack3, ActorRef.noSender());
            underTest.tell(ack4, ActorRef.noSender());
            underTest.tell(ack2, ActorRef.noSender());

            // THEN
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            assertThat(acks.getSize()).isEqualTo(2);
            assertThat(acks.getAcknowledgement(label1)).contains(ack1);
            assertThat(acks.getAcknowledgement(label2)).contains(ack2);
        }};
    }

    private Props getAcknowledgementAggregatorProps(final ThingModifyCommand<?> command, final TestKit testKit) {
        return AcknowledgementAggregatorActor.props(command, config, headerTranslator, tellThis(testKit));
    }

    private static Consumer<Object> tellThis(final TestKit testKit) {
        return result -> testKit.getRef().tell(result, ActorRef.noSender());
    }
}
