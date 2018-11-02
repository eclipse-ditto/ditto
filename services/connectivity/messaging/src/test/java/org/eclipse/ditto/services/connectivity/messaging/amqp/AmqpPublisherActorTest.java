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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.qpid.jms.JmsSession;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.TestProbe;

public class AmqpPublisherActorTest extends AbstractPublisherActorTest<JmsMessage> {


    private JmsSession session;
    private MessageProducer messageProducer;

    @Override
    protected void setupMocks(final TestProbe probe) throws JMSException {
        session = mock(JmsSession.class);
        messageProducer = mock(MessageProducer.class);
        when(session.createProducer(ArgumentMatchers.any(Destination.class))).thenReturn(messageProducer);
        when(session.createTextMessage(anyString())).thenAnswer((Answer<JmsMessage>) invocation -> {
            final String argument = invocation.getArgument(0);
            final JmsTextMessage jmsTextMessage = new JmsTextMessage(new AmqpJmsTextMessageFacade());
            jmsTextMessage.setText(argument);
            return jmsTextMessage;
        });
    }

    @Override
    protected Props getPublisherActorProps() {
        return AmqpPublisherActor.props(session);
    }

    @Override
    protected void verifyPublishedMessage() throws Exception {
        final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);

        verify(messageProducer, timeout(1000)).send(messageCaptor.capture(), any(CompletionListener.class));

        final Message message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        System.out.println(Collections.list(message.getPropertyNames()));
        assertThat(message.getStringProperty("thing_id")).isEqualTo(TestConstants.Things.THING_ID);
        assertThat(message.getStringProperty("suffixed_thing_id")).isEqualTo(
                TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(message.getStringProperty("prefixed_thing_id")).isEqualTo(
                "some.prefix." + TestConstants.Things.THING_ID);
        assertThat(message.getStringProperty("eclipse")).isEqualTo("ditto");
        assertThat(message.getStringProperty("device_id")).isEqualTo(TestConstants.Things.THING_ID);
    }

    @Override
    protected void publisherCreated(final ActorRef publisherActor) {
        // nothing to do
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    protected String getOutboundAddress() {
        return "outbound";
    }
}
