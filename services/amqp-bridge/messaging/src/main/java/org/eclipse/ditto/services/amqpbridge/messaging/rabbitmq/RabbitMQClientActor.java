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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.services.amqpbridge.messaging.BaseClientActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.AmqpBridgeModifyCommand;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;

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

/**
 * Actor which handles connection to AMQP 0.9.1 server.
 */
public class RabbitMQClientActor extends BaseClientActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private ActorRef connectionActor;
    private ActorRef channelActor;

    private RabbitMQClientActor(final String connectionId, final ActorRef connectionActor) {
        super(connectionId, connectionActor);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @return the Akka configuration Props object
     * @param connectionActor the corresponding {@code ConnectionActor}
     */
    public static Props props(final String connectionId, final ActorRef connectionActor) {
        return Props.create(RabbitMQClientActor.class, new Creator<RabbitMQClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQClientActor create() {
                return new RabbitMQClientActor(connectionId, connectionActor);
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
                .build()
                .orElse(initHandling);
    }

    private void handleConnect(final CreateConnection connect) {
        amqpConnection = connect.getAmqpConnection();
        mappingContexts = connect.getMappingContexts();
        connect();
    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        this.channelActor = channelCreated.channel();
        startCommandProcessor();
        startCommandConsumers();
    }

    private void connect() {
        if (connectionActor == null) {
            log.debug("Connecting to {}", amqpConnection.getUri());
            final ConnectionFactory connectionFactory =
                    AmqpConnectionBasedRabbitConnectionFactory.createConnection(amqpConnection);

            final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                    com.newmotion.akka.rabbitmq.ConnectionActor.props$default$2(),
                    com.newmotion.akka.rabbitmq.ConnectionActor.props$default$3());
            connectionActor = startChildActor("rmq-connection-" + amqpConnection.getId(), props);
            connectionActor.tell(
                    CreateChannel.apply(
                            ChannelActor.props((channel, s) -> null),
                            Option.apply("consumer-channel")), self());
            log.debug("Connection '{}' opened.", amqpConnection.getId());
        } else {
            log.debug("Connection '{}' is already open.", amqpConnection.getId());
        }
        getSender().tell(new Status.Success("connected"), self());
    }

    private void handleDisconnect(final AmqpBridgeModifyCommand<?> cmd) {
        stopCommandConsumers();
        stopCommandProcessor();
        if (channelActor != null) {
            stopChildActor(channelActor);
            channelActor = null;
        }
        if (connectionActor != null) {
            stopChildActor(connectionActor);
            connectionActor = null;
        }
        getSender().tell(new Status.Success("disconnected"), self());
    }

    private void stopCommandConsumers() {
        amqpConnection.getSources().forEach(source -> stopChildActor("consumer-" + source));
    }

    private void startCommandConsumers() {
        log.info("Channel created, start to consume queues...");
        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            ensureQueuesExist(channel);
            startConsumers(channel);
            return null;
        }, false);
        channelActor.tell(channelMessage, self());
    }

    private void startConsumers(final Channel channel) {
        amqpConnection.getSources().forEach(source -> {
            final ActorRef commandConsumer =
                    startChildActor("consumer-" + source, CommandConsumerActor.props(commandProcessor));
            try {
                final String consumerTag =
                        channel.basicConsume(source, false, new RabbitMQMessageConsumer(commandConsumer, channel));
                log.debug("Consuming queue {}, consumer tag is {}", source, consumerTag);
            } catch (IOException e) {
                log.warning("Failed to consume queue '{}': {}", source, e.getMessage());
            }
        });
    }

    private void ensureQueuesExist(final Channel channel) {
        final List<String> missingQueues = new ArrayList<>();
        amqpConnection.getSources().forEach(source -> {
            try {
                channel.queueDeclarePassive(source);
            } catch (IOException e) {
                missingQueues.add(source);
                log.warning("The queue '{}' does not exits.", source);
            }
        });
        if (!missingQueues.isEmpty()) {
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
            } catch (Exception e) {
                log.info("Failed to process delivery {}: {}", envelope.getDeliveryTag(), e.getMessage());
            } finally {
                try {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                } catch (IOException e) {
                    log.info("Failed to ack delivery {}: {}", envelope.getDeliveryTag(), e.getMessage());
                }
            }
        }

    }
}