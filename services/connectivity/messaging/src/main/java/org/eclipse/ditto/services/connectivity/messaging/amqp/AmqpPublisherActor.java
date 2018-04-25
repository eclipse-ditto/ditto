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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Responsible for creating JMS {@link MessageProducer}s and sending {@link ExternalMessage}s as JMSMessages to those.
 */
public final class AmqpPublisherActor extends BasePublisherActor<AmqpTarget> {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "amqpPublisherActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Session session;
    private final Map<Destination, MessageProducer> producerMap;

    private AddressMetric addressMetric;
    private long publishedMessages = 0L;
    private Instant lastMessagePublishedAt;


    private AmqpPublisherActor(final Session session, final Set<Target> targets) {
        super(targets);
        this.session = checkNotNull(session, "session");
        this.producerMap = new HashMap<>();
        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpPublisherActor}.
     *
     * @param session the jms session
     * @param targets the targets to publish to
     * @return the Akka configuration Props object.
     */
    static Props props(final Session session, final Set<Target> targets) {
        return Props.create(AmqpPublisherActor.class, new Creator<AmqpPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpPublisherActor create() {
                return new AmqpPublisherActor(session, targets);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExternalMessage.class, this::isResponseOrError, response -> {
                    final String correlationId = response.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received response or error {} ", response);

                    final String replyToFromHeader = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyToFromHeader != null) {
                        final AmqpTarget amqpTarget = AmqpTarget.fromTargetAddress(replyToFromHeader);
                        sendMessage(amqpTarget, response);
                    } else {
                        log.info("Response dropped, missing replyTo address: {}", response);
                    }
                })
                .match(ExternalMessage.class, message -> {
                    final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped message {} ", message);

                    final Set<AmqpTarget> destinationForMessage = getDestinationForMessage(message);
                    destinationForMessage.forEach(amqpTarget -> sendMessage(amqpTarget, message));
                })
                .match(AddressMetric.class, this::handleAddressMetric)
                .match(RetrieveAddressMetric.class, ram -> {
                    getSender().tell(ConnectivityModelFactory.newAddressMetric(
                            addressMetric.getStatus(),
                            addressMetric.getStatusDetails().orElse(null),
                            publishedMessages, lastMessagePublishedAt), getSelf());
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    protected AmqpTarget toPublishTarget(final String address) {
        return AmqpTarget.fromTargetAddress(address);
    }

    private void handleAddressMetric(final AddressMetric addressMetric) {
        this.addressMetric = addressMetric;
    }

    private void sendMessage(final AmqpTarget target, final ExternalMessage message) {
        try {
            final MessageProducer producer = getProducer(target.getJmsDestination());
            if (producer != null) {
                final Message jmsMessage = toJmsMessage(message);
                producer.send(jmsMessage);
                publishedMessages++;
                lastMessagePublishedAt = Instant.now();
            } else {
                log.warning("No producer for destination {} available.", target);
            }
        } catch (final JMSException e) {
            log.info("Failed to send JMS response: {}", e.getMessage());
        }
    }

    private Message toJmsMessage(final ExternalMessage externalMessage) throws JMSException {
        final Message message;
        final Optional<String> optTextPayload = externalMessage.getTextPayload();
        if (optTextPayload.isPresent()) {
            message = session.createTextMessage(optTextPayload.get());
        } else if (externalMessage.getBytePayload().isPresent()) {
            final BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(externalMessage.getBytePayload().map(ByteBuffer::array).orElse(new byte[]{}));
            message = bytesMessage;
        } else {
            message = session.createMessage();
        }
        if (message instanceof JmsMessage) {
            final JmsMessageFacade facade = ((JmsMessage) message).getFacade();
            if (facade instanceof AmqpJmsMessageFacade) {
                final AmqpJmsMessageFacade amqpJmsMessageFacade = (AmqpJmsMessageFacade) facade;
                externalMessage.getHeaders()
                        .forEach((key, value) -> {
                            try {
                                amqpJmsMessageFacade.setApplicationProperty(key, value);
                            } catch (final JMSException e) {
                                log.warning("Could not set application-property <{}>", key);
                            }
                        });
            }
        }
        message.setJMSCorrelationID(externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey()));
        return message;
    }

    @Nullable
    private MessageProducer getProducer(final Destination destination) {
        return Optional.of(destination)
                .map(t -> producerMap.computeIfAbsent(destination, this::createMessageProducer))
                .orElse(null);
    }

    @Nullable
    private MessageProducer createMessageProducer(final Destination destination) {
        log.debug("Creating AMQP Producer for '{}'", destination);
        try {
            return session.createProducer(destination);
        } catch (final JMSException e) {
            log.warning("Could not create producer for {}.", destination);
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
