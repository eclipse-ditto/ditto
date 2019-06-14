/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Responsible for publishing {@link ExternalMessage}s into RabbitMQ / AMQP 0.9.1.
 * <p>
 * To receive responses the {@code replyTo} header must be set. Responses are sent to the default exchange with the
 * {@code replyTo} header as routing key.
 * </p>
 * The {@code address} of the {@code targets} from the {@link Connection} are interpreted as follows:
 * <ul>
 * <li>no {@code targets} defined: signals are not published at all</li>
 * <li>{@code address="target/routingKey"}: signals are published to exchange {@code target} with routing key {@code
 * routingKey}</li>
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

    @SuppressWarnings("unused")
    private RabbitMQPublisherActor(final List<Target> targets, final String connectionId) {
        super(connectionId, targets);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQPublisherActor}.
     *
     * @param connectionId the connectionId this publisher belongs to
     * @param targets the targets to publish to
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final List<Target> targets) {

        return Props.create(RabbitMQPublisherActor.class, targets, connectionId);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(ChannelCreated.class, channelCreated -> {
                    this.channelActor = channelCreated.channel();

                    final Set<String> exchanges = targets.stream()
                            .map(t -> toPublishTarget(t.getAddress()))
                            .map(RabbitMQTarget::getExchange)
                            .collect(Collectors.toSet());
                    final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
                        exchanges.forEach(exchange -> {
                            log.debug("Checking for existence of exchange <{}>", exchange);
                            try {
                                channel.exchangeDeclarePassive(exchange);
                            } catch (final IOException e) {
                                log.warning("Failed to declare exchange <{}> passively", exchange);
                                targets.stream()
                                        .filter(t ->
                                                exchange.equals(toPublishTarget(t.getAddress()).getExchange())
                                        )
                                        .findFirst()
                                        .ifPresent(target ->
                                                resourceStatusMap.put(
                                                        target,
                                                        ConnectivityModelFactory.newTargetStatus(
                                                                InstanceIdentifierSupplier.getInstance().get(),
                                                                ConnectivityStatus.FAILED,
                                                                target.getAddress(),
                                                                "Exchange '" + exchange + "' was missing at " +
                                                                        Instant.now())));
                            }
                        });
                        return null;
                    }, false);
                    channelCreated.channel().tell(channelMessage, getSelf());
                });
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected RabbitMQTarget toPublishTarget(final String address) {
        return RabbitMQTarget.fromTargetAddress(address);
    }

    @Override
    protected RabbitMQTarget toReplyTarget(final String replyToAddress) {
        return RabbitMQTarget.of(DEFAULT_EXCHANGE, replyToAddress);
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final RabbitMQTarget publishTarget,
            final ExternalMessage message,
            ConnectionMonitor publishedMonitor) {
        if (channelActor == null) {
            log.info("No channel available, dropping response.");
            return;
        }

        if (publishTarget.getRoutingKey() == null) {
            log.warning("No routing key, dropping message.");
            return;
        }

        final Map<String, String> messageHeaders = message.getHeaders();
        final String contentType = messageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER);
        final String correlationId = messageHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey());

        final Map<String, Object> stringObjectMap = messageHeaders.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Object) e.getValue()));

        final AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType(contentType)
                .correlationId(correlationId)
                .headers(stringObjectMap)
                .build();

        final byte[] body;
        if (message.isTextMessage()) {
            body = message.getTextPayload()
                    .map(text -> text.getBytes(CharsetDeterminer.getInstance().apply(contentType)))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert text to bytes."));
        } else {
            body = message.getBytePayload()
                    .map(ByteBuffer::array)
                    .orElse(new byte[]{});
        }

        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            try {
                log.debug("Publishing to exchange <{}> and routing key <{}>: {}", publishTarget.getExchange(),
                        publishTarget.getRoutingKey(), basicProperties);
                channel.basicPublish(publishTarget.getExchange(), publishTarget.getRoutingKey(), basicProperties,
                        body);
                publishedMonitor.success(message);
            } catch (final Exception e) {
                log.warning("Failed to publish message to RabbitMQ: {}", e.getMessage());
                publishedMonitor.exception(message, e);
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
    }

}
