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
package org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.ExternalMessageBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

public class CommandConsumerActor extends AbstractActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String EXCHANGE_HEADER = "exchange";

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";
    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef commandProcessor;

    private CommandConsumerActor(@Nullable final ActorRef commandProcessor) {
        this.commandProcessor = checkNotNull(commandProcessor, "commandProcessor");
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @return the Akka configuration Props object.
     */
    static Props props(@Nullable final ActorRef commandProcessor) {
        return Props.create(
                CommandConsumerActor.class, new Creator<CommandConsumerActor>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public CommandConsumerActor create() {
                        return new CommandConsumerActor(commandProcessor);
                    }
                });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Delivery.class, this::handleDelivery)
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleDelivery(final Delivery delivery) {
        final BasicProperties properties = delivery.getProperties();
        final Envelope envelope = delivery.getEnvelope();
        final byte[] body = delivery.getBody();
        try {
            final String correlationId = properties.getCorrelationId();
            LogUtil.enhanceLogWithCorrelationId(log, correlationId);
            final Map<String, String> headers = extractHeadersFromMessage(properties, envelope);
            // TODO TJ how can we be sure that the message is a command at this point? could be anything ..
            final ExternalMessageBuilder externalMessageBuilder =
                    AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers);
            final String contentType = properties.getContentType();
            if (shouldBeInterpretedAsText(contentType)) {
                final String text = new String(body, determineCharset(contentType));
                externalMessageBuilder.withText(text);
            } else {
                externalMessageBuilder.withBytes(body);
            }
            final ExternalMessage externalMessage = externalMessageBuilder.build();
            log.debug("Received message from RabbitMQ ({}//{}): {}", envelope, properties);
            commandProcessor.forward(externalMessage, context());
        } catch (final Exception e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage(), e);
        }
    }

    private static boolean shouldBeInterpretedAsText(@Nullable final String contentType) {
        return contentType != null && (contentType.startsWith(TEXT_PLAIN) || contentType.startsWith(APPLICATION_JSON));
    }

    private static Charset determineCharset(@Nullable final CharSequence contentType) {
        if (contentType != null) {
            final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
            if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
                return Charset.forName(withCharset[1]);
            }
        }
        return StandardCharsets.UTF_8;
    }

    private Map<String, String> extractHeadersFromMessage(final BasicProperties properties, final Envelope envelope) {
        final Map<String, String> headersFromProperties =
                Optional.ofNullable(properties.getHeaders())
                        .map(Map::entrySet)
                        .map(this::setToStringStringMap).orElseGet(HashMap::new);

        // set headers specific to rmq messages
        // TODO DG is this still required?
        if (envelope.getExchange() != null) {
            headersFromProperties.put(EXCHANGE_HEADER, envelope.getExchange());
        }
        if (properties.getReplyTo() != null) {
            headersFromProperties.put(ExternalMessage.REPLY_TO_HEADER, properties.getReplyTo());
        }
        if (properties.getCorrelationId() != null) {
            headersFromProperties.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), properties.getCorrelationId());
        }
        if (properties.getContentType() != null) {
            headersFromProperties.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), properties.getContentType());
        }
        headersFromProperties.put(MESSAGE_ID_HEADER, Long.toString(envelope.getDeliveryTag()));
        return headersFromProperties;
    }

    private Map<String, String> setToStringStringMap(final Set<Map.Entry<String, Object>> entries) {
        return entries.stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

}
