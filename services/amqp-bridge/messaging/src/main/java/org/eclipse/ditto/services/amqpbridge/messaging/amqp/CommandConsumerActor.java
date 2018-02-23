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

import static java.util.stream.Collectors.toMap;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@code CommandProcessorActor}.
 */
final class CommandConsumerActor extends AbstractActor implements MessageListener {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";

    private static final String CORRELATION_ID_HEADER = "correlation-id";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final String source;
    private final MessageConsumer messageConsumer;
    private final ActorRef commandProcessor;

    private CommandConsumerActor(final String source, final MessageConsumer messageConsumer,
            final ActorRef commandProcessor) {
        this.source = checkNotNull(source, "source");
        this.messageConsumer = checkNotNull(messageConsumer);
        this.commandProcessor = checkNotNull(commandProcessor, "commandProcessor");
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @param source the source of messages
     * @param messageConsumer the JMS message consumer
     * @param commandProcessor the command processor where received messages are forwarded to
     * @return the Akka configuration Props object.
     */
    static Props props(final String source, final MessageConsumer messageConsumer, final ActorRef commandProcessor) {
        return Props.create(CommandConsumerActor.class, new Creator<CommandConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandConsumerActor create() {
                return new CommandConsumerActor(source, messageConsumer, commandProcessor);
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
        messageConsumer.setMessageListener(this);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        try {
            log.debug("Closing AMQP Consumer for '{}'", source);
            messageConsumer.close();
        } catch (final JMSException jmsException) {
            log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                    jmsException.getMessage());
        }
    }

    @Override
    public void onMessage(final Message message) {
        try {
            final Map<String, String> headers = extractHeadersMapFromJmsMessage(message);
            final InternalMessage.Builder builder = InternalMessage.Builder.newCommand(headers);
            extractPayloadFromMessage(message, builder);
            final InternalMessage internalMessage = builder.build();
            log.debug("Forwarding to processor: {}, {}", internalMessage.getHeaders(),
                    internalMessage.getTextPayload().orElse("binary"));
            commandProcessor.tell(internalMessage, self());
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
        } catch (final Exception e) {
            log.info("Unexpected Exception: {}", e.getMessage());
        }
    }

    private void extractPayloadFromMessage(final Message message,
            final InternalMessage.Builder builder) throws JMSException {
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

    private Map<String, String> extractHeadersMapFromJmsMessage(final Message message) throws JMSException {
        @SuppressWarnings("unchecked") final List<String> names = Collections.list(message.getPropertyNames());
        final Map<String, String> headersFromJmsProperties = names.stream()
                .map(key -> getPropertyAsEntry(message, key))
                .filter(Objects::nonNull)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        final String replyTo = message.getJMSReplyTo() != null ? String.valueOf(message.getJMSReplyTo()) : null;
        if (replyTo != null) {
            headersFromJmsProperties.put("reply-to", replyTo);
        }

        final String jmsCorrelationId = message.getJMSCorrelationID() != null ? message.getJMSCorrelationID() :
                message.getJMSMessageID();
        if (jmsCorrelationId != null) {
            headersFromJmsProperties.put(CORRELATION_ID_HEADER, jmsCorrelationId);
        }
        return headersFromJmsProperties;
    }

    @Nullable
    private Map.Entry<String, String> getPropertyAsEntry(final Message message, final String key) {
        try {
            return new AbstractMap.SimpleImmutableEntry<>(key, message.getObjectProperty(key).toString());
        } catch (final JMSException e) {
            log.debug("Property '{}' could not be read, dropping...", key);
            return null;
        }
    }
}
