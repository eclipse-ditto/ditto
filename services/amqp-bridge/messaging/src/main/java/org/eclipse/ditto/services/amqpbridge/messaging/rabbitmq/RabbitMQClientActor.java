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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.amqpbridge.messaging.BaseClientActor;
import org.eclipse.ditto.services.models.amqpbridge.AmqpBridgeMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.AmqpBridgeModifyCommand;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import com.newmotion.akka.rabbitmq.ChannelActor;
import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.newmotion.akka.rabbitmq.CreateChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import scala.Option;
import scala.concurrent.duration.Duration;

/**
 * Actor which handles connection to AMQP 0.9.1 server.
 */
public class RabbitMQClientActor extends BaseClientActor {

    private static final String RMQ_CONNECTION_PREFIX = "rmq-connection-";
    private static final String RMQ_PUBLISHER_PREFIX = "rmq-publisher-";
    private static final String CONSUMER_CHANNEL = "consumer-channel";
    private static final String PUBLISHER_CHANNEL = "publisher-channel";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable private ActorRef rmqConnectionActor;
    @Nullable private ActorRef consumerChannelActor;
    @Nullable private ActorRef rmqPublisherActor;

    private RabbitMQClientActor(final String connectionId, final ActorRef rmqConnectionActor) {
        super(connectionId, rmqConnectionActor, AmqpBridgeMessagingConstants.GATEWAY_PROXY_ACTOR_PATH);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId the connection id
     * @param rmqConnectionActor the corresponding {@code ConnectionActor}
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef rmqConnectionActor) {
        return Props.create(RabbitMQClientActor.class, new Creator<RabbitMQClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQClientActor create() {
                return new RabbitMQClientActor(connectionId, rmqConnectionActor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, this::handleConnect)
                .match(CloseConnection.class, this::handleDisconnect)
                .match(DeleteConnection.class, this::handleDisconnect)
                .match(ChannelCreated.class, this::handleChannelCreated)
                .match(ThingEvent.class, this::handleThingEvent)
                .build()
                .orElse(initHandling);
    }

    private void handleThingEvent(final ThingEvent<?> thingEvent) {
        if (commandProcessor != null) {
            commandProcessor.tell(thingEvent, self());
        }
    }

    private void handleConnect(final CreateConnection connect) {
        amqpConnection = connect.getAmqpConnection();
        mappingContexts = connect.getMappingContexts();

        // reset receive timeout when CreateConnection is received
        getContext().setReceiveTimeout(Duration.Undefined());

        connect();
    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        this.consumerChannelActor = channelCreated.channel();
        startCommandConsumers();
    }

    private void connect() {
        if (rmqConnectionActor == null && amqpConnection != null) {
            final ConnectionFactory connectionFactory =
                    AmqpConnectionBasedRabbitConnectionFactory.createConnection(amqpConnection);

            final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                    com.newmotion.akka.rabbitmq.ConnectionActor.props$default$2(),
                    com.newmotion.akka.rabbitmq.ConnectionActor.props$default$3());
            rmqConnectionActor = startChildActor(RMQ_CONNECTION_PREFIX + connectionId, props);

            final Props publisherProps = RabbitMQPublisherActor.props(amqpConnection);
            rmqPublisherActor =
                    startChildActor(RabbitMQPublisherActor.ACTOR_NAME_PREFIX + connectionId, publisherProps);

            startCommandProcessor(rmqPublisherActor);

            // create publisher channel
            rmqConnectionActor.tell(
                    CreateChannel.apply(
                            ChannelActor.props((channel, s) -> null),
                            Option.apply(PUBLISHER_CHANNEL)), rmqPublisherActor);

            // create a consumer channel - if source is configured
            if (isConsumingCommands()) {
                rmqConnectionActor.tell(
                        CreateChannel.apply(
                                ChannelActor.props((channel, s) -> null),
                                Option.apply(CONSUMER_CHANNEL)), self());
            }
            log.debug("Connection '{}' opened.", connectionId);
        } else {
            log.debug("Connection '{}' is already open.", connectionId);
        }
        getSender().tell(new Status.Success("connected"), self());
    }

    private void handleDisconnect(final AmqpBridgeModifyCommand<?> cmd) {
        log.debug("Handling <{}> command: {}", cmd.getType(), cmd);
        stopCommandConsumers();
        stopCommandProcessor();
        stopCommandPublisher();
        if (consumerChannelActor != null) {
            stopChildActor(consumerChannelActor);
            consumerChannelActor = null;
        }
        if (rmqConnectionActor != null) {
            stopChildActor(rmqConnectionActor);
            rmqConnectionActor = null;
        }
        if (rmqPublisherActor != null) {
            stopChildActor(rmqPublisherActor);
            rmqPublisherActor = null;
        }
        getSender().tell(new Status.Success("disconnected"), self());
    }

    private void stopCommandPublisher() {
        stopChildActor(RMQ_PUBLISHER_PREFIX + connectionId);
    }

    private void stopCommandConsumers() {
        getSourcesOrEmptySet().forEach(source -> stopChildActor("consumer-" + source));
    }

    private void startCommandConsumers() {
        log.info("Channel created, start to consume queues...");
        if (consumerChannelActor == null) {
            log.info("No consumerChannelActor, cannot consume queues without a channel.");
        } else {
            final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
                ensureQueuesExist(channel);
                startConsumers(channel);
                return null;
            }, false);
            consumerChannelActor.tell(channelMessage, self());
        }
    }

    private void startConsumers(final Channel channel) {
        getSourcesOrEmptySet().forEach(source -> {
            final ActorRef commandConsumer =
                    startChildActor("consumer-" + source, CommandConsumerActor.props(commandProcessor));
            try {
                final String consumerTag =
                        channel.basicConsume(source, false,
                                new RabbitMQMessageConsumer(commandConsumer, channel));
                log.debug("Consuming queue {}, consumer tag is {}", source, consumerTag);
            } catch (final IOException e) {
                log.warning("Failed to consume queue '{}': {}", source, e.getMessage());
            }
        });
    }

    private void ensureQueuesExist(final Channel channel) {
        final List<String> missingQueues = new ArrayList<>();
        getSourcesOrEmptySet().forEach(source -> {
            try {
                channel.queueDeclarePassive(source);
            } catch (final IOException e) {
                missingQueues.add(source);
                log.warning("The queue '{}' does not exits.", source);
            }
        });
        if (!missingQueues.isEmpty()) {
            // TODO TJ IllegalStateException here means the parent actor needs to handle that - is that intended?
            throw new IllegalStateException("The queues " + missingQueues + " are missing.");
        }
    }

    private class RabbitMQMessageConsumer extends DefaultConsumer {

        private final ActorRef commandConsumer;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        private RabbitMQMessageConsumer(final ActorRef commandConsumer, final Channel channel) {
            super(channel);
            this.commandConsumer = commandConsumer;
        }

        @Override
        public void handleDelivery(final String consumerTag, final Envelope envelope,
                final AMQP.BasicProperties properties, final byte[] body) {
            try {
                commandConsumer.tell(new Delivery(envelope, properties, body), RabbitMQClientActor.this.self());
            } catch (final Exception e) {
                log.info("Failed to process delivery {}: {}", envelope.getDeliveryTag(), e.getMessage());
            } finally {
                try {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                } catch (final IOException e) {
                    log.info("Failed to ack delivery {}: {}", envelope.getDeliveryTag(), e.getMessage());
                }
            }
        }

    }
}
