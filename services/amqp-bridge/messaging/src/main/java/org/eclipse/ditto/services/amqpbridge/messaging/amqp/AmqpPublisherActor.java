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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
                .match(CommandResponse.class,
                        response -> Optional.ofNullable(response.getDittoHeaders().get("reply-to"))
                                .flatMap(this::getProducer)
                                .ifPresent(producer -> sendSignal(producer, response)))
                .match(ThingEvent.class,
                        event -> amqpConnection
                                .getEventTarget()
                                .flatMap(this::getProducer)
                                .ifPresent(producer -> sendSignal(producer, event)))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void sendSignal(final MessageProducer messageProducer, final Signal<?> signal) {
        try {
            final Message message = session.createTextMessage(signal.toJsonString());
            message.setJMSCorrelationID(signal.getDittoHeaders().getCorrelationId().orElse("no-cid"));
            messageProducer.send(message);
        } catch (JMSException e) {
            log.info("Failed to send response: {}", e.getMessage());
        }
    }

    private Optional<MessageProducer> getProducer(final String target) {
        return Optional.of(target)
                .filter(String::isEmpty)
                .map(t -> producerMap.computeIfAbsent(target, this::createMessageProducer));
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
            } catch (JMSException jmsException) {
                log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                        jmsException.getMessage());
            }
        });
    }
}
