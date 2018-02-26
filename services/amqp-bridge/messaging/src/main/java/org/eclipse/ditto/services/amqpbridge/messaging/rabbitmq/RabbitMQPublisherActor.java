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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

public class RabbitMQPublisherActor extends AbstractActor {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "rmqPublisherActor-";
    private static final String REPLY_TO_HEADER = "replyTo";
    private static final String DEFAULT_EXCHANGE = "";
    private static final String DEFAULT_EVENT_ROUTING_KEY = "thingEvent";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final AmqpConnection amqpConnection;
    @Nullable private ActorRef channelActor;

    private RabbitMQPublisherActor(@Nullable final AmqpConnection amqpConnection) {
        this.amqpConnection = checkNotNull(amqpConnection, "amqpConnection");
    }


    /**
     * Creates Akka configuration object {@link Props} for this {@code CommandConsumerActor}.
     *
     * @param amqpConnection the amqp connection configuration
     * @return the Akka configuration Props object.
     */
    static Props props(@Nullable final AmqpConnection amqpConnection) {
        return Props.create(RabbitMQPublisherActor.class, new Creator<RabbitMQPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQPublisherActor create() {
                return new RabbitMQPublisherActor(amqpConnection);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ChannelCreated.class, channelCreated -> this.channelActor = channelCreated.channel())
                .match(ExternalMessage.class, this::isResponseOrError, response -> {
                    final String correlationId =
                            response.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received response {} ", response);
                    final String exchange = amqpConnection.getReplyTarget().orElse(DEFAULT_EXCHANGE);
                    final String routingKey = response.getHeaders().get(REPLY_TO_HEADER);
                    if (routingKey != null) {
                        publishMessage(exchange, routingKey, response);
                    } else {
                        log.debug("Response dropped due to missing replyTo address.");
                    }
                })
                .match(ExternalMessage.class, ExternalMessage::isEvent, event -> {
                    final String correlationId = event.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.info("Received event {} ", event);

                    if (amqpConnection.getEventTarget().isPresent()) {
                        final String exchange = amqpConnection.getEventTarget().get();
                        publishMessage(exchange, DEFAULT_EVENT_ROUTING_KEY, event);
                    } else {
                        log.info("Dropping event, no target exchange configured.");
                    }
                })
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private boolean isResponseOrError(final ExternalMessage message) {
        return message.isCommandResponse() || message.isError();
    }

    private void publishMessage(final String exchange, final String routingKey, final ExternalMessage message) {

        if (channelActor == null) {
            log.info("No channel available, dropping response.");
            return;
        }

        final String contentType = message.getHeaders().get("content-type");
        final String correlationId = message.getHeaders().get("correlation-id");

        final Map<String, Object> stringObjectMap =
                message.getHeaders().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        (e -> (Object) e.getValue())));

        final AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().contentType(contentType)
                .correlationId(correlationId)
                .headers(stringObjectMap)
                .build();

        final byte[] body;
        if (message.isTextMessage()) {
            body = message.getTextPayload()
                    .map(text -> text.getBytes(StandardCharsets.UTF_8))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert text to bytes."));
        } else {
            body = message.getBytePayload()
                    .map(ByteBuffer::array)
                    .orElseThrow(() -> new IllegalArgumentException("Byte payload was empty."));
        }

        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            try {
                channel.basicPublish(exchange, routingKey, basicProperties, body);
            } catch (final Exception e) {
                log.info("Failed to publish message to RabbitMQ: {}", e.getMessage());
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, self());
    }

}
