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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
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
import org.eclipse.ditto.model.connectivity.Target;
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
 * <p>
 * To receive responses the {@code replyTo} header must be set. Responses are sent to the default exchange with
 * the {@code replyTo} header as routing key.
 * </p>
 * The {@code address} of the {@code targets} from the {@link Connection} are interpreted as follows:
 * <ul>
 * <li>no {@code targets} defined: signals are not published at all</li>
 * <li>{@code address="target/routingKey"}: signals are published to exchange {@code target} with routing key {@code routingKey}</li>
 * </ul>
 */
public final class RabbitMQPublisherActor extends BasePublisherActor<RabbitMQTarget> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "rmqPublisherActor";

    private static final String DEFAULT_EXCHANGE = "";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable private ActorRef channelActor;

    private long publishedMessages = 0L;
    private Instant lastMessagePublishedAt;
    @Nullable private AddressMetric addressMetric;

    private RabbitMQPublisherActor(final Set<Target> targets) {
        super(targets);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQPublisherActor}.
     *
     * @param targets the targets to publish to
     * @return the Akka configuration Props object.
     */
    static Props props(final Set<Target> targets) {
        return Props.create(RabbitMQPublisherActor.class, new Creator<RabbitMQPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQPublisherActor create() {
                return new RabbitMQPublisherActor(targets);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ChannelCreated.class, channelCreated -> {
                    this.channelActor = channelCreated.channel();
                    addressMetric = ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                            0, null);

                    final Set<String> exchanges = getDestinations().values()
                            .stream()
                            .flatMap(Set::stream)
                            .map(RabbitMQTarget::getExchange)
                            .collect(Collectors.toSet());
                    final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
                        exchanges.forEach(exchange -> {
                            log.debug("Checking for existence of exchange <{}>", exchange);
                            try {
                                channel.exchangeDeclarePassive(exchange);
                            } catch (final IOException e) {
                                log.warning("Failed to declare exchange <{}> passively", exchange);
                                addressMetric = ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                                        "Exchange '" + exchange + "' was missing at " + Instant.now(), 0, null);
                            }
                        });
                        return null;
                    }, false);
                    channelCreated.channel().tell(channelMessage, getSelf());
                })
                .match(ExternalMessage.class, this::isResponseOrError, response -> {
                    final String correlationId =
                            response.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped response {} ", response);

                    final String replyTo = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyTo != null) {
                        final RabbitMQTarget replyTarget = RabbitMQTarget.of(DEFAULT_EXCHANGE, replyTo);
                        publishMessage(replyTarget, response);
                    } else {
                        log.info("Response dropped, missing replyTo address: {}", response);
                    }
                })
                .match(ExternalMessage.class, message -> {
                    final String correlationId =
                            message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped message {} ", message);

                    final Set<RabbitMQTarget> destinationForMessage = getDestinationForMessage(message);
                    log.debug("Publishing message to targets <{}>: {} ", destinationForMessage, message);
                    destinationForMessage.forEach(destination -> publishMessage(destination, message));
                })
                .match(AddressMetric.class, this::handleAddressMetric)
                .match(RetrieveAddressMetric.class, ram -> {
                    getSender().tell(ConnectivityModelFactory.newAddressMetric(
                            addressMetric != null ? addressMetric.getStatus() : ConnectionStatus.UNKNOWN,
                            addressMetric != null ? addressMetric.getStatusDetails().orElse(null) : null,
                            publishedMessages, lastMessagePublishedAt), getSelf());
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
            log.warning("No routing key, dropping message.");
            return;
        }

        publishedMessages++;
        lastMessagePublishedAt = Instant.now();

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
                log.debug("Publishing to exchange <{}> and routing key <{}>: {}", rabbitMQTarget.getExchange(),
                        rabbitMQTarget.getRoutingKey(), basicProperties);
                channel.basicPublish(rabbitMQTarget.getExchange(), rabbitMQTarget.getRoutingKey(), basicProperties,
                        body);
            } catch (final Exception e) {
                log.warning("Failed to publish message to RabbitMQ: {}", e.getMessage());
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
    }

}
