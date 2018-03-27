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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Responsible for publishing {@link ExternalMessage}s into RabbitMQ / AMQP 0.9.1.
 * The behaviour how the events and responses are published can be controlled by the configuration settings
 * {@code eventTarget} and {@code replyTarget}.
 * <p>
 * The {@code eventTarget} from the {@link Connection} is interpreted as follows:
 * </p>
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
public final class RabbitMQPublisherActor extends BasePublisherActor<RabbitMQTarget> {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "rmqPublisherActor-";
    private static final String DEFAULT_EXCHANGE = "";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable private ActorRef channelActor;

    private long publishedMessages = 0L;
    @Nullable private AddressMetric addressMetric = null;

    private RabbitMQPublisherActor(final Connection connection) {
        super(connection);
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
                .match(ExternalMessage.class, this::isResponseOrError, message -> {
                    final String correlationId =
                            message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped message {} ", message);

                    final String replyTo = message.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    final RabbitMQTarget replyTarget = RabbitMQTarget.of(DEFAULT_EXCHANGE, replyTo);
                    publishMessage(replyTarget, message);
                })
                .match(ExternalMessage.class, message -> {
                    final String correlationId =
                            message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped message {} ", message);

                    final Set<RabbitMQTarget> destinationForMessage = getDestinationForMessage(message);
                    log.debug("Publishing message to {} to targets {}", message, destinationForMessage);
                    destinationForMessage.forEach(destination -> publishMessage(destination, message));
                })
                .match(AddressMetric.class, this::handleAddressMetric)
                .match(RetrieveAddressMetric.class, ram -> {
                    getSender().tell(ConnectivityModelFactory.newAddressMetric(
                            addressMetric != null ? addressMetric.getStatus() : ConnectionStatus.UNKNOWN,
                            addressMetric != null ? addressMetric.getStatusDetails().orElse(null) : null,
                            publishedMessages), getSelf());
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    protected RabbitMQTarget toPublishTarget(final String address) {
        return RabbitMQTarget.fromTargetAddress(address);
    }

    private void handleAddressMetric(final AddressMetric addressMetric) {
        this.addressMetric = addressMetric;
    }

    private void publishMessage(final RabbitMQTarget rabbitMQTarget, final ExternalMessage message) {
        if (channelActor == null) {
            log.info("No channel available, dropping response.");
            return;
        }

        if (rabbitMQTarget.getRoutingKey() == null) {
            log.debug("No routing key, dropping message.");
            return;
        }

        publishedMessages++;

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
                channel.basicPublish(rabbitMQTarget.getExchange(), rabbitMQTarget.getRoutingKey(), basicProperties,
                        body);
            } catch (final Exception e) {
                log.info("Failed to publish message to RabbitMQ: {}", e.getMessage());
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
    }

}
