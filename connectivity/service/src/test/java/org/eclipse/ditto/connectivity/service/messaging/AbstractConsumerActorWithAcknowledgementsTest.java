/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementRequestTimeoutException;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Extends {@code AbstractConsumerActorTest} with acknowledgement tests.
 *
 * @param <M> the message type
 */
public abstract class AbstractConsumerActorWithAcknowledgementsTest<M> extends AbstractConsumerActorTest<M> {

    protected abstract Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final Set<AcknowledgementRequest> acknowledgementRequests);

    protected abstract void verifyMessageSettlement(final TestKit testKit,
            boolean isSuccessExpected, final boolean shouldRedeliver)
            throws Exception;

    @Test
    public void testPositiveSourceAcknowledgementSettlement() throws Exception {
        testSourceAcknowledgementSettlement(true, true, modifyThing ->
                        ModifyThingResponse.modified(modifyThing.getEntityId(), modifyThing.getDittoHeaders()),
                TestConstants.MODIFY_THING_WITH_ACK, publishMappedMessage ->
                        assertThat(publishMappedMessage.getOutboundSignal()
                                .first()
                                .getExternalMessage()
                                .getInternalHeaders()
                                .getAcknowledgementRequests()
                                .stream()
                                .map(AcknowledgementRequest::getLabel)
                                .toList()
                        ).containsExactly(TWIN_PERSISTED)
        );
    }

    @Test
    public void testNegativeSourceAcknowledgementSettlementDueToError() throws Exception {
        testSourceAcknowledgementSettlement(false, false, modifyThing ->
                        ThingNotAccessibleException.newBuilder(modifyThing.getEntityId())
                                .dittoHeaders(modifyThing.getDittoHeaders())
                                .build(), TestConstants.MODIFY_THING_WITH_ACK,
                publishMappedMessage -> {
                    final Signal<?> response = publishMappedMessage.getOutboundSignal()
                            .first()
                            .getSource();
                    assertThat(response).isInstanceOf(ThingErrorResponse.class);
                    final ThingErrorResponse errorResponse = (ThingErrorResponse) response;
                    assertThat(errorResponse.getDittoRuntimeException()).isInstanceOf(
                            ThingNotAccessibleException.class);
                    assertThat(errorResponse.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(errorResponse.getDittoHeaders().getCorrelationId()).contains("cid");
                }
        );
    }

    @Test
    public void testNegativeSourceAcknowledgementSettlementDueToNAck() throws Exception {
        testSourceAcknowledgementSettlement(false, false, modifyThing ->
                        Acknowledgement.of(AcknowledgementLabel.of("twin-persisted"), modifyThing.getEntityId(),
                                HttpStatus.BAD_REQUEST, modifyThing.getDittoHeaders()),
                TestConstants.MODIFY_THING_WITH_ACK,
                null
        );
    }

    @Test
    public void testNegativeSourceAcknowledgementSettlementDueToTimeout() throws Exception {
        testSourceAcknowledgementSettlement(false, true, modifyThing ->
                        AcknowledgementRequestTimeoutException.newBuilder(Duration.ofSeconds(1L))
                                .dittoHeaders(modifyThing.getDittoHeaders())
                                .build(), TestConstants.MODIFY_THING_WITH_ACK,
                publishMappedMessage -> {
                    final Signal<?> response = publishMappedMessage.getOutboundSignal()
                            .first()
                            .getSource();
                    assertThat(response).isInstanceOf(ThingErrorResponse.class);
                    final ThingErrorResponse errorResponse = (ThingErrorResponse) response;
                    assertThat(errorResponse.getDittoRuntimeException()).isInstanceOf(
                            AcknowledgementRequestTimeoutException.class);
                    assertThat(errorResponse.getHttpStatus()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
                    assertThat(errorResponse.getDittoHeaders().getCorrelationId()).contains("cid");
                }
        );
    }

    @Test
    public void testNegativeSourceAcknowledgementSettlementDueToServerError() throws Exception {
        testSourceAcknowledgementSettlement(false, true, modifyThing ->
                        ThingUnavailableException.newBuilder(modifyThing.getEntityId())
                                .dittoHeaders(modifyThing.getDittoHeaders())
                                .build(), TestConstants.MODIFY_THING_WITH_ACK,
                publishMappedMessage -> {
                    final Signal<?> response = publishMappedMessage.getOutboundSignal()
                            .first()
                            .getSource();
                    assertThat(response).isInstanceOf(ThingErrorResponse.class);
                    final ThingErrorResponse errorResponse = (ThingErrorResponse) response;
                    assertThat(errorResponse.getDittoRuntimeException()).isInstanceOf(
                            ThingUnavailableException.class);
                    assertThat(errorResponse.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(errorResponse.getDittoHeaders().getCorrelationId()).contains("cid");
                }
        );
    }

    private void testSourceAcknowledgementSettlement(final boolean isSuccessExpected,
            final boolean shouldRedeliver,
            final Function<ModifyThing, Object> responseCreator,
            final String payload,
            @Nullable final Consumer<BaseClientActor.PublishMappedMessage> messageConsumer)
            throws Exception {

        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final TestProbe proxy = TestProbe.apply(actorSystem);
            final TestProbe clientActor = TestProbe.apply(actorSystem);

            final Sink<Object, NotUsed> inboundMappingSink =
                    setupInboundMappingSink(clientActor.ref(), proxy.ref());
            final ActorRef underTest = childActorOf(getConsumerActorProps(inboundMappingSink, Collections.emptySet()));

            underTest.tell(getInboundMessage(payload, TestConstants.header("device_id", TestConstants.Things.THING_ID)),
                    sender.ref());

            final ModifyThing modifyThing = proxy.expectMsgClass(ModifyThing.class);
            assertThat((CharSequence) modifyThing.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            proxy.reply(responseCreator.apply(modifyThing));

            if (null != messageConsumer) {
                messageConsumer.accept(clientActor.expectMsgClass(BaseClientActor.PublishMappedMessage.class));
            }
            verifyMessageSettlement(this, isSuccessExpected, shouldRedeliver);
        }};
    }

}
