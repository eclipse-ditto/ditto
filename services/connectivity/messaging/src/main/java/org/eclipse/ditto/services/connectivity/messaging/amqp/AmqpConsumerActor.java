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

import static java.util.stream.Collectors.toMap;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@code MessageMappingProcessorActor}.
 */
final class AmqpConsumerActor extends AbstractActor implements MessageListener {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String sourceAddress;
    private final MessageConsumer messageConsumer;
    private final ActorRef messageMappingProcessor;

    private AddressMetric addressMetric;
    private long consumedMessages = 0L;
    private Instant lastMessageConsumedAt;

    private AmqpConsumerActor(final String sourceAddress, final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor) {
        this.sourceAddress = checkNotNull(sourceAddress, "source");
        this.messageConsumer = checkNotNull(messageConsumer);
        this.messageMappingProcessor = checkNotNull(messageMappingProcessor, "messageMappingProcessor");
        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpConsumerActor}.
     *
     * @param source the source of messages
     * @param messageConsumer the JMS message consumer
     * @param messageMappingProcessor the message mapping processor where received messages are forwarded to
     * @return the Akka configuration Props object.
     */
    static Props props(final String source, final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor) {
        return Props.create(AmqpConsumerActor.class, new Creator<AmqpConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpConsumerActor create() {
                return new AmqpConsumerActor(source, messageConsumer, messageMappingProcessor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(JmsMessage.class, this::handleJmsMessage)
                .match(AddressMetric.class, this::handleAddressMetric)
                .match(RetrieveAddressMetric.class, ram -> {
                    getSender().tell(ConnectivityModelFactory.newAddressMetric(
                            addressMetric.getStatus(),
                            addressMetric.getStatusDetails().orElse(null),
                            consumedMessages, lastMessageConsumedAt), getSelf());
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    public void preStart() throws JMSException {
        messageConsumer.setMessageListener(this);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        try {
            log.debug("Closing AMQP Consumer for '{}'", sourceAddress);
            messageConsumer.close();
        } catch (final JMSException jmsException) {
            log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                    jmsException.getMessage());
        }
    }

    @Override
    public void onMessage(final Message message) {
        getSelf().tell(message, ActorRef.noSender());
    }

    private void handleAddressMetric(final AddressMetric addressMetric) {
        this.addressMetric = addressMetric;
    }

    private void handleJmsMessage(final JmsMessage message) {
        consumedMessages++;
        lastMessageConsumedAt = Instant.now();
        try {
            final Map<String, String> headers = extractHeadersMapFromJmsMessage(message);
            headers.put(DittoHeaderDefinition.SOURCE.getKey(), sourceAddress);
            final ExternalMessageBuilder builder = ConnectivityModelFactory.newExternalMessageBuilder(headers);
            extractPayloadFromMessage(message, builder);
            final ExternalMessage externalMessage = builder.build();
            LogUtil.enhanceLogWithCorrelationId(log, externalMessage
                    .findHeader(DittoHeaderDefinition.CORRELATION_ID.getKey()));
            if (log.isDebugEnabled()) {
                log.debug("Received message from AMQP 1.0 ({}): {}", externalMessage.getHeaders(),
                        externalMessage.getTextPayload().orElse("binary"));
            }
            messageMappingProcessor.forward(externalMessage, getContext());
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
        } catch (final Exception e) {
            log.info("Unexpected {}: {}", e.getClass().getName(), e.getMessage());
        } finally {
            try {
                // we use the manual acknowledge mode so we always have to ack the message
                message.acknowledge();
            } catch (final JMSException e) {
                log.error(e, "Failed to ack an AMQP message");
            }
        }
    }

    private void extractPayloadFromMessage(final Message message,
            final ExternalMessageBuilder builder) throws JMSException {
        if (message instanceof TextMessage) {
            final String payload = ((TextMessage) message).getText();
            builder.withText(payload);
        } else if (message instanceof BytesMessage) {
            final BytesMessage bytesMessage = (BytesMessage) message;
            final long bodyLength = bytesMessage.getBodyLength();
            if (bodyLength >= Integer.MIN_VALUE && bodyLength <= Integer.MAX_VALUE) {
                final int length = (int) bodyLength;
                final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                bytesMessage.readBytes(byteBuffer.array());
                builder.withBytes(byteBuffer);
            } else {
                throw new IllegalArgumentException("Message too large...");
            }
        } else {
            throw new IllegalArgumentException("Only messages of type TEXT or BYTE are supported.");
        }
    }

    private Map<String, String> extractHeadersMapFromJmsMessage(final JmsMessage message) throws JMSException {

        final Map<String, String> headersFromJmsProperties;

        final JmsMessageFacade facade = message.getFacade();
        if (facade instanceof AmqpJmsMessageFacade) {
            final AmqpJmsMessageFacade amqpJmsMessageFacade = (AmqpJmsMessageFacade) facade;
            final Set<String> names =
                    amqpJmsMessageFacade.getApplicationPropertyNames(amqpJmsMessageFacade.getPropertyNames());
            headersFromJmsProperties = new HashMap<>(names.stream()
                    .map(key -> getPropertyAsEntry(amqpJmsMessageFacade, key))
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

            final String contentType = amqpJmsMessageFacade.getContentType();
            headersFromJmsProperties.put(ExternalMessage.CONTENT_TYPE_HEADER, contentType);
        } else {
            throw new JMSException("Message facade was not of type AmqpJmsMessageFacade");
        }

        final String replyTo = message.getJMSReplyTo() != null ? String.valueOf(message.getJMSReplyTo()) : null;
        if (replyTo != null) {
            headersFromJmsProperties.put(ExternalMessage.REPLY_TO_HEADER, replyTo);
        }

        final String jmsCorrelationId = message.getJMSCorrelationID() != null ? message.getJMSCorrelationID() :
                message.getJMSMessageID();
        if (jmsCorrelationId != null) {
            headersFromJmsProperties.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), jmsCorrelationId);
        }

        return headersFromJmsProperties;
    }

    @Nullable
    private Map.Entry<String, String> getPropertyAsEntry(final AmqpJmsMessageFacade message, final String key) {
        try {
            return new AbstractMap.SimpleImmutableEntry<>(key, message.getApplicationProperty(key).toString());
        } catch (final JMSException e) {
            log.debug("Property '{}' could not be read, dropping...", key);
            return null;
        }
    }
}
