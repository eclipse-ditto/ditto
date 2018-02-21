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
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
                .match(ChannelCreated.class, channelCreated -> {
                    this.channelActor = channelCreated.channel();
                })
                .match(InternalMessage.class, response -> {
                    // ...
                })
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, response);
                    final String replyTo;
                    final String replyToFromHeaders = response.getDittoHeaders().get(REPLY_TO_HEADER);
                    if (amqpConnection.getReplyTarget().isPresent()) {
                        replyTo = amqpConnection.getReplyTarget().get();
                    } else if (replyToFromHeaders != null) {
                        replyTo = replyToFromHeaders;
                    } else {
                        replyTo = null;
                    }

                    if (replyTo != null) {
                        // TODO this must be done in Mapper
                        final InternalMessage message =
                                new InternalMessage.Builder(response.getDittoHeaders()).withText(
                                        response.toJsonString())
                                        .build();
                        publishMessage(DEFAULT_EXCHANGE, replyTo, message);
                    } else {
                        log.debug("No replyTo found in configuration or in message header, dropping reply.");
                    }
                })
                .match(ThingEvent.class, event -> {
                    if (amqpConnection.getEventTarget().isPresent()) {
                        final String eventTarget = amqpConnection.getEventTarget().get();
                        log.debug("Received thing event. Will publish to {}/{}.", eventTarget, event.getType());
                        final InternalMessage message =
                                new InternalMessage.Builder(event.getDittoHeaders()).withText(event.toJsonString())
                                        .build();
                        publishMessage(eventTarget, event.getType(), message);
                    }
                })
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }


    private void publishMessage(final String exchange, final String routingKey, InternalMessage message) {

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
            } catch (Exception e) {
                log.info("Failed to publish message to RabbitMQ: {}", e.getMessage());
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, self());
    }

}
