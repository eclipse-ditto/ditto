/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

import java.util.Collections;

import javax.jms.JMSException;

import org.apache.qpid.jms.message.JmsMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.TestProbe;

public class RabbitMQPublisherActorTest extends AbstractPublisherActorTest<JmsMessage> {


    private TestProbe probe;

    @Override
    protected void setupMocks(final TestProbe probe) throws JMSException {
        this.probe = probe;
    }

    @Override
    protected void publisherCreated(final ActorRef publisherActor) {
        final ChannelCreated channelCreated = ChannelCreated.apply(probe.ref());
        publisherActor.tell(channelCreated, ActorRef.noSender());
    }

    @Override
    protected Props getPublisherActorProps() {
        return RabbitMQPublisherActor.props(Collections.emptySet());
    }

    @Override
    protected void verifyPublishedMessage() throws Exception {
        final Channel channel = mock(Channel.class);

        // omit first channel message
        probe.expectMsgClass(ChannelMessage.class);
        final ChannelMessage channelMessage = probe.expectMsgClass(ChannelMessage.class);

        System.out.println("message:" + channelMessage);

        channelMessage.onChannel().apply(channel);

        final ArgumentCaptor<AMQP.BasicProperties> propertiesCaptor =
                ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        final ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);

        Mockito.verify(channel, timeout(1000)).basicPublish(eq("exchange"), eq("outbound"), propertiesCaptor.capture(),
                bodyCaptor.capture());

        assertThat(propertiesCaptor.getValue().getHeaders().get("thing_id")).isEqualTo(TestConstants.Things.THING_ID);
        assertThat(propertiesCaptor.getValue().getHeaders().get("suffixed_thing_id")).isEqualTo(
                TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(propertiesCaptor.getValue().getHeaders().get("prefixed_thing_id")).isEqualTo(
                "some.prefix." + TestConstants.Things.THING_ID);
        assertThat(propertiesCaptor.getValue().getHeaders().get("eclipse")).isEqualTo("ditto");
        assertThat(propertiesCaptor.getValue().getHeaders().get("device_id")).isEqualTo(TestConstants.Things.THING_ID);
    }

    protected String getOutboundAddress() {
        return "exchange/outbound";
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }
}
