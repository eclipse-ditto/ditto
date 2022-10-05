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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public class RabbitMQPublisherActorTest extends AbstractPublisherActorTest {

    private TestProbe probe;

    @Test
    public void testMultipleAcknowledgements() throws Exception {
        new TestKit(actorSystem) {{

            // GIVEN: there is a multi-mapped message with 6 different acknowledgements
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final int signalCount = 6;
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(
                            getMockOutboundSignalWithAutoAck("rabbit1",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat()),
                            getMockOutboundSignalWithAutoAck("rabbit2",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat()),
                            getMockOutboundSignalWithAutoAck("rabbit3",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat()),
                            getMockOutboundSignalWithAutoAck("rabbit4",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat()),
                            getMockOutboundSignalWithAutoAck("rabbit5",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat()),
                            getMockOutboundSignalWithAutoAck("rabbit6",
                                    DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    getRef().path().toSerializationFormat())
                    ), getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            // WHEN: publisher actor is created
            final Pair<Channel, ConfirmListener> pair = setUpPublishConfirmMode();
            final Channel channel = pair.first();
            final ConfirmListener confirmListener = pair.second();

            // WHEN: publisher actor is told to publish a multi-mapped message with 6 different acks
            publisherActor.tell(multiMapped, getRef());

            // THEN: publisher actor should tell channel to publish 6 messages with incrementing sequence numbers
            for (int i = 0; i < signalCount; ++i) {
                probe.expectMsgClass(ChannelMessage.class).onChannel().apply(channel);
            }

            // WHEN: broker acknowledges messages 1-3 positively and 4-6 negatively
            confirmListener.handleNack(6, false);
            confirmListener.handleNack(4, false);
            confirmListener.handleNack(5, false);
            confirmListener.handleAck(3, true);

            // THEN: publisher actor sends an aggregated acks message to the original sender containing each status
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            for (final Acknowledgement ack : acks.getSuccessfulAcknowledgements()) {
                assertThat(ack.getLabel().toString()).isBetween("rabbit1", "rabbit3");
                assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.OK);
            }
            for (final Acknowledgement ack : acks.getFailedAcknowledgements()) {
                assertThat(ack.getLabel().toString()).isBetween("rabbit4", "rabbit6");
                assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            }
        }};
    }

    @Override
    protected void setupMocks(final TestProbe probe) {
        this.probe = probe;
    }

    @Override
    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        final ChannelCreated channelCreated = ChannelCreated.apply(probe.ref());
        publisherActor.tell(channelCreated, ActorRef.noSender());
    }

    @Override
    protected Props getPublisherActorProps() {
        return RabbitMQPublisherActor.props(TestConstants.createConnection(),
                mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()));
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) throws Exception {
        final Pair<Channel, ConfirmListener> channelAndListener = setUpPublishConfirmMode();
        final CompletableFuture<Acknowledgements> acksFuture = CompletableFuture.supplyAsync(ackSupplier);
        probe.expectMsgClass(ChannelMessage.class).onChannel().apply(channelAndListener.first());
        channelAndListener.second().handleAck(1L, false);

        final Acknowledgements acks = acksFuture.join();
        assertThat(acks.getSize()).describedAs("Expect 1 acknowledgement in: " + acks).isEqualTo(1);
        final Acknowledgement ack = acks.stream().findAny().orElseThrow();
        assertThat(ack.getLabel().toString()).describedAs("Ack label").hasToString("please-verify");
        assertThat(ack.getHttpStatus()).describedAs("Ack status").isEqualTo(HttpStatus.OK);
    }

    @Override
    protected void verifyPublishedMessage() throws Exception {
        // set up publish confirm with first channel message
        final Channel channel = setUpPublishConfirmMode().first();

        final ChannelMessage channelMessage = probe.expectMsgClass(ChannelMessage.class);

        channelMessage.onChannel().apply(channel);

        final ArgumentCaptor<AMQP.BasicProperties> propertiesCaptor =
                ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        final ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(channel, timeout(1000)).basicPublish(eq("exchange"), eq("outbound"), eq(true),
                propertiesCaptor.capture(), bodyCaptor.capture());

        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("thing_id", TestConstants.Things.THING_ID.toString());
        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("suffixed_thing_id", TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("prefixed_thing_id", "some.prefix." + TestConstants.Things.THING_ID);
        assertThat(propertiesCaptor.getValue().getHeaders()).containsEntry("eclipse", "ditto");
        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("device_id", TestConstants.Things.THING_ID.toString());
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() throws Exception {
        final Channel channel = mock(Channel.class);

        // omit first channel message
        probe.expectMsgClass(ChannelMessage.class);
        final ChannelMessage channelMessage = probe.expectMsgClass(ChannelMessage.class);

        channelMessage.onChannel().apply(channel);

        final ArgumentCaptor<AMQP.BasicProperties> propertiesCaptor =
                ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        final ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(channel, timeout(1000)).basicPublish(
                eq("replyTarget"),
                eq("thing:id"),
                eq(true),
                propertiesCaptor.capture(),
                bodyCaptor.capture());

        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("correlation-id", TestConstants.CORRELATION_ID);
        assertThat(propertiesCaptor.getValue().getHeaders())
                .containsEntry("mappedHeader2", "thing:id");
    }

    protected String getOutboundAddress() {
        return "exchange/outbound";
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    private Pair<Channel, ConfirmListener> setUpPublishConfirmMode() {
        try {
            final Channel channel = mock(Channel.class);
            final AtomicLong nextPublishSeqNo = new AtomicLong(0L);
            final AtomicReference<ConfirmListener> confirmListenerBox = new AtomicReference<>();
            when(channel.getNextPublishSeqNo()).thenAnswer(args -> nextPublishSeqNo.incrementAndGet());
            doAnswer(arg -> {
                confirmListenerBox.set(arg.getArgument(0));
                return null;
            }).when(channel).addConfirmListener(any());

            final ChannelMessage channelMessage = probe.expectMsgClass(ChannelMessage.class);
            channelMessage.onChannel().apply(channel);
            verify(channel).confirmSelect();
            assertThat(confirmListenerBox)
                    .describedAs("Publisher actor should set up confirm mode")
                    .doesNotHaveValue(null);

            return Pair.create(channel, confirmListenerBox.get());
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

}
