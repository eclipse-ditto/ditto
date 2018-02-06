/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@link CommandProcessorActor}.
 */
final class CommandConsumerActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final Session session;
    private final String source;
    private final ActorRef commandProcessor;

    private MessageConsumer consumer;

    private CommandConsumerActor(final Session session, final String source, final ActorRef commandProcessor) {
        this.session = session;
        this.source = source;
        this.commandProcessor = commandProcessor;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @param session the jms session.
     * @param source
     * @param commandProcessor
     * @return the Akka configuration Props object.
     */
    static Props props(final Session session, final String source, final ActorRef commandProcessor) {
        return Props.create(CommandConsumerActor.class, new Creator<CommandConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandConsumerActor create() {
                return new CommandConsumerActor(session, source, commandProcessor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    public void preStart() throws JMSException {
        final Destination destination = new JmsQueue(source);
        log.info("Creating AMQP Consumer for '{}'", source);
        if (session != null) {
            consumer = session.createConsumer(destination);
            consumer.setMessageListener((Message message) -> commandProcessor.forward(message, getContext()));
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        log.info("Closing AMQP Consumer for '{}'", source);
        if (consumer != null) {
            consumer.close();
        }
    }

}
