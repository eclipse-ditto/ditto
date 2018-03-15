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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
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

/**
 * Responsible for publishing {@link ExternalMessage}s into RabbitMQ / AMQP 0.9.1.
 * The behaviour how the events and responses are published can be controlled by the configuration settings
 * {@code eventTarget} and {@code replyTarget}.
 * <p/>
 * The {@code eventTarget} from the {@link Connection} is interpreted as follows:
 * <ul>
 *     <li>{@code eventTarget} undefined: thing events are not published at all</li>
 *     <li>{@code eventTarget="target"}: thing events are published to exchange
 *         {@code target} with default routing key {@code thingEvent}</li>
 *     <li>{@code eventTarget="target/routingKey"}: thing events are published to exchange
 *         {@code target} with routing key {@code routingKey}</li>
 * </ul>
 * The {@code replyTarget} together with the {@code replyTo} header is interpreted as follows:
 * <ul>
 *     <li>{@code replyTarget} and {@code replyTo} header undefined: the response is dropped</li>
 *     <li>{@code replyTarget="target"} and {@code replyTo} undefined: the response is dropped</li>
 *     <li>{@code replyTarget} undefined and header {@code replyTo="replyKey"}: the response is sent to the
 *         default exchange ({@code ""}) with routing key {@code replyKey}</li>
 *     <li>{@code replyTarget="target"} and header {@code replyTo="replyKey"}, the response is sent to the
 *         exchange {@code target} with routing key {@code replyKey}</li>
 *     <li>{@code replyTarget="target/replyKey"} and {@code replyTo} header undefined: the response is sent to the exchange {@code target} with routing key
 *         {@code replyKey}</li>
 *     <li>{@code replyTarget="target/replyKey"} and header {@code replyTo="replyKeyFromHeader"}: the response is sent to the exchange {@code target} with routing key
 *         {@code replyKeyFromHeader}. Note: This means the {@code replyTo} header takes precedence over the configured routing key.</li>
 * </ul>
 */
public final class RabbitMQPublisherActor extends AbstractActor {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "rmqPublisherActor-";

    private static final String DEFAULT_EXCHANGE = "";
    private static final String DEFAULT_EVENT_ROUTING_KEY = "thingEvent";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final Connection connection;
    @Nullable private ActorRef channelActor;

    private RabbitMQPublisherActor(final Connection connection) {
        this.connection = checkNotNull(connection, "connection");
    }


    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQPublisherActor}.
     *
     * @param connection the connection configuration
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection) {
        return Props.create(RabbitMQPublisherActor.class, new Creator<RabbitMQPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQPublisherActor create() {
                return new RabbitMQPublisherActor(connection);
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
                    final String exchange =
                            connection.getReplyTarget().flatMap(this::getExchangeFromTarget).orElse(DEFAULT_EXCHANGE);
                    final Optional<String> routingKeyFromTarget =
                            connection.getReplyTarget().flatMap(this::getRoutingKeyFromTarget);
                    final Optional<String> routingKeyFromHeader =
                            Optional.ofNullable(response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER));
                    final String routingKey = routingKeyFromHeader.orElse(routingKeyFromTarget.orElse(null));
                    if (routingKey != null) {
                        publishMessage(exchange, routingKey, response);
                    } else {
                        log.info("Dropping response, no routingKey defined via config or replyTo header.");
                    }
                })
                .match(ExternalMessage.class, ExternalMessage::isEvent, event -> {
                    final String correlationId = event.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.info("Received event {} ", event);

                    if (connection.getEventTarget().isPresent()) {
                        final String exchange = connection.getEventTarget().get();
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

    /**
     * For RabbitMQ connections the target can have the following format: [exchange]/[routingKey].
     *
     * @param target the configured target
     * @return the exchange part of the target, {@link Optional#EMPTY} otherwise.
     */
    private Optional<String> getExchangeFromTarget(final String target) {
        return Optional.of(target.split("/"))
                .filter(segments -> segments.length > 0)
                .map(segments -> segments[0])
                .filter(exchange -> !exchange.isEmpty());
    }

    /**
     * For RabbitMQ connections the target can have the following format: [exchange]/[routingKey].
     *
     * @param target the configured target
     * @return the optional routing key part
     */
    private Optional<String> getRoutingKeyFromTarget(final String target) {
        return Optional.of(target.split("/"))
                .filter(segments -> segments.length == 2)
                .map(segments -> segments[1])
                .filter(routingKey -> !routingKey.isEmpty());
    }

    private boolean isResponseOrError(final ExternalMessage message) {
        return message.isCommandResponse() || message.isError();
    }

    private void publishMessage(final String exchange, final String routingKey, final ExternalMessage message) {

        if (channelActor == null) {
            log.info("No channel available, dropping response.");
            return;
        }

        final String contentType = message.getHeaders().get(ExternalMessage.CONTENT_TYPE_HEADER);
        final String correlationId = message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());

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
                    .map(text -> text.getBytes(MessageMappers.determineCharset(contentType)))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert text to bytes."));
        } else {
            body = message.getBytePayload()
                    .map(ByteBuffer::array)
                    .orElse(new byte[]{});
        }

        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            try {
                channel.basicPublish(exchange, routingKey, basicProperties, body);
            } catch (final Exception e) {
                log.info("Failed to publish message to RabbitMQ: {}", e.getMessage());
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
    }

}
