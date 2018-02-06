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
package org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.services.amqpbridge.messaging.InternalMessage;
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

    private static final String REPLY_TO_HEADER = "replyTo";
    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String EXCHANGE_HEADER = "exchange";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef commandProcessor;

    private CommandConsumerActor(final ActorRef commandProcessor) {
        this.commandProcessor = ConditionChecker.checkNotNull(commandProcessor, "commandProcessor");
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @return the Akka configuration Props object.
     */
    static Props props(final ActorRef targetActor) {
        return Props.create(
                CommandConsumerActor.class, new Creator<CommandConsumerActor>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public CommandConsumerActor create() {
                        return new CommandConsumerActor(targetActor);
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

            final DittoHeadersBuilder dittoHeadersBuilder = Optional.ofNullable(properties.getHeaders())
                    .map(Map::entrySet)
                    .map(this::setToStringStringMap)
                    .map(DittoHeaders::newBuilder)
                    .orElseGet(DittoHeaders::newBuilder);

            // set headers specific to rmq messages
            if (envelope.getExchange() != null) {
                dittoHeadersBuilder.putHeader(EXCHANGE_HEADER, envelope.getExchange());
            }
            if (properties.getReplyTo() != null) {
                dittoHeadersBuilder.putHeader(REPLY_TO_HEADER, properties.getReplyTo());
            }
            dittoHeadersBuilder.putHeader(MESSAGE_ID_HEADER, Long.toString(envelope.getDeliveryTag()));

            final String commandJsonString = new String(body, StandardCharsets.UTF_8);
            log.debug("Received Thing Protocol Command from RabbitMQ ({}//{}): {}", envelope, properties,
                    commandJsonString);

            final InternalMessage.Ack ackMessage = new InternalMessage.Ack<>(envelope.getDeliveryTag());
            commandProcessor.forward(
                    new InternalMessage(ackMessage, dittoHeadersBuilder.build(), commandJsonString),
                    context());
        } catch (Exception e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage(), e);
        }
    }

    private Map<String, String> setToStringStringMap(final Set<Map.Entry<String, Object>> entries) {
        return entries.stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
    }

}
