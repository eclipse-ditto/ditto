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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.amqp.JmsExceptionThrowingBiConsumer.wrap;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectionMetricsCollector;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.ActorRef;
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

    private static final Map<String, BiConsumer<Message, String>> JMS_HEADER_MAPPING = new HashMap<>();

    static {
        JMS_HEADER_MAPPING.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), wrap(Message::setJMSCorrelationID));
        JMS_HEADER_MAPPING.put("message-id", wrap(Message::setJMSMessageID));
        JMS_HEADER_MAPPING.put("reply-to", wrap((message, value) -> message.setJMSReplyTo(new JmsQueue(value))));
        JMS_HEADER_MAPPING.put("subject", wrap(Message::setJMSType));
        JMS_HEADER_MAPPING.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), wrap((message, value) -> {
            if (message instanceof JmsMessage) {
                final JmsMessageFacade facade = ((JmsMessage) message).getFacade();
                if (facade instanceof AmqpJmsMessageFacade) {
                    ((AmqpJmsMessageFacade) facade).setContentType(value);
                }
            }
        }));
    }

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Session session;
    private final Map<Destination, MessageProducer> producerMap;

    private AmqpPublisherActor(final String connectionId, final Session session) {
        super(connectionId);
        this.session = checkNotNull(session, "session");
        this.producerMap = new HashMap<>();
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpPublisherActor}.
     *
     * @param connectionId the id of the connection this publisher belongs to
     * @param session the jms session
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final Session session) {
        return Props.create(AmqpPublisherActor.class, new Creator<AmqpPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpPublisherActor create() {
                return new AmqpPublisherActor(connectionId, session);
            }
        });
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected AmqpTarget toPublishTarget(final String address) {
        return AmqpTarget.fromTargetAddress(address);
    }

    @Override
    protected AmqpTarget toReplyTarget(final String replyToAddress) {
        return AmqpTarget.fromTargetAddress(replyToAddress);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final AmqpTarget publishTarget,
            final ExternalMessage message, ConnectionMetricsCollector publishedCounter) {
        try {
            final MessageProducer producer = getProducer(publishTarget.getJmsDestination());
            if (producer != null) {
                final Message jmsMessage = toJmsMessage(message);

                final ActorRef origin = getSender();
                producer.send(jmsMessage, new CompletionListener() {
                    @Override
                    public void onCompletion(final Message message) {
                        publishedCounter.recordSuccess();
                        log.debug("Message {} sent successfully.", message);
                    }

                    @Override
                    public void onException(final Message messageFailedToSend, final Exception exception) {
                        publishedCounter.recordFailure();
                        handleSendException(message, exception, origin);
                    }
                });
            } else {
                log.warning("No producer for destination {} available.", publishTarget);
                final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                        .message("Failed to send message, no producer available.")
                        .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                        .build();
                getSender().tell(sendFailedException, getSelf());
            }
        } catch (final JMSException e) {
            handleSendException(message, e, getSender());
        }
    }

    private void handleSendException(final ExternalMessage message, final Exception e, final ActorRef sender) {
        log.info("Failed to send JMS message: [{}] {}", e.getClass().getSimpleName(), e.getMessage());
        final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                .cause(e)
                .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                .build();
        sender.tell(sendFailedException, getSelf());
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

        // some headers must be handled differently to be passed to amqp message
        final Map<String, String> headers = externalMessage.getHeaders();
        JMS_HEADER_MAPPING.entrySet().stream()
                .filter(entry -> headers.containsKey(entry.getKey()))
                .forEach(entry -> entry.getValue().accept(message, headers.get(entry.getKey())));

        if (message instanceof JmsMessage) {
            final JmsMessageFacade facade = ((JmsMessage) message).getFacade();
            if (facade instanceof AmqpJmsMessageFacade) {
                final AmqpJmsMessageFacade amqpJmsMessageFacade = (AmqpJmsMessageFacade) facade;
                externalMessage.getHeaders()
                        .entrySet()
                        .stream()
                        // skip special jms properties in generic mapping
                        .filter(h -> !JMS_HEADER_MAPPING.keySet().contains(h.getKey()))
                        .forEach(entry -> {
                            try {
                                amqpJmsMessageFacade.setApplicationProperty(entry.getKey(), entry.getValue());
                            } catch (final JMSException ex) {
                                log.warning("Could not set application-property <{}>", entry.getKey());
                            }
                        });
            }
        }
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

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }
}
