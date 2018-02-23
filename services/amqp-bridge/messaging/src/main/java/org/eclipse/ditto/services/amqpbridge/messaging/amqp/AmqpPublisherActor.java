/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

public class AmqpPublisherActor extends AbstractActor {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "amqpPublisherActor";
    private static final String REPLY_TO_HEADER = "replyTo";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Session session;
    private final AmqpConnection amqpConnection;
    private final Map<String, MessageProducer> producerMap;

    private AmqpPublisherActor(@Nullable final Session session, @Nullable final AmqpConnection amqpConnection) {
        this.session = checkNotNull(session, "session");
        this.amqpConnection = checkNotNull(amqpConnection, "amqpConnection");
        this.producerMap = new HashMap<>();
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @param session the jms session.
     * @return the Akka configuration Props object.
     */
    static Props props(@Nullable final Session session, @Nullable final AmqpConnection amqpConnection) {
        return Props.create(AmqpPublisherActor.class, new Creator<AmqpPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpPublisherActor create() {
                return new AmqpPublisherActor(session, amqpConnection);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(InternalMessage.class, InternalMessage::isCommandResponse, response -> {
                    final String correlationId = response.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received command response {} ", response);
                    final String replyToFromHeader = response.getHeaders().get(REPLY_TO_HEADER);
                    final String replyToFromConfig = amqpConnection.getReplyTarget().orElse(null);
                    if (replyToFromHeader != null) {
                        sendMessage(replyToFromHeader, response);
                    } else if (replyToFromConfig != null) {
                        sendMessage(replyToFromConfig, response);
                    } else {
                        log.debug("Response dropped, missing replyTo address.");
                    }
                })
                .match(InternalMessage.class, InternalMessage::isCommandResponse, event -> {
                    final String correlationId = event.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received command response {} ", event);
                    amqpConnection.getEventTarget().ifPresent(target -> sendMessage(target, event));
                })
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void sendMessage(final String target, final InternalMessage message) {
        try {
            final MessageProducer producer = getProducer(target);
            if (producer != null) {
                final Message jmsMessage = toJmsMessage(message);
                producer.send(jmsMessage);
            }
        } catch (final JMSException e) {
            log.info("Failed to send JMS response: {}", e.getMessage());
        }
    }

    private Message toJmsMessage(final InternalMessage internal) throws JMSException {
        final Message message;
        if (internal.getTextPayload().isPresent()) {
            message = session.createTextMessage(internal.getTextPayload().get());
        } else if (internal.getBytePayload().isPresent()) {
            final BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(internal.getBytePayload().get().array());
            message = bytesMessage;
        } else {
            throw new IllegalArgumentException("Only byte or text are supported, dropping.");
        }
        message.setJMSCorrelationID(internal.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey()));
        return message;
    }

    @Nullable
    private MessageProducer getProducer(final String target) {
        return Optional.of(target)
                .filter(String::isEmpty)
                .map(t -> producerMap.computeIfAbsent(target, this::createMessageProducer)).orElse(null);
    }

    @Nullable
    private MessageProducer createMessageProducer(final String target) {
        final Destination destination = new JmsQueue(target);
        log.debug("Creating AMQP Producer for '{}'", target);
        try {
            return session.createProducer(destination);
        } catch (JMSException e) {
            log.warning("Could not create producer for {}.", target);
            return null;
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        producerMap.forEach((target, producer) -> {
            try {
                log.debug("Closing AMQP Producer for '{}'", target);
                producer.close();
            } catch (final JMSException jmsException) {
                log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                        jmsException.getMessage());
            }
        });
    }
}
