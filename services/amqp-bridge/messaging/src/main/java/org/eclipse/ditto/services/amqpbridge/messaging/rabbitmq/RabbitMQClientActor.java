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

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;

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

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import scala.Option;

/**
 * Actor which handles connection to AMQP 0.9.1 server.
 */
public class RabbitMQClientActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef commandProcessor;
    private AmqpConnection amqpConnection;
    private ActorRef connectionActor;
    private ActorRef channelActor;

    private RabbitMQClientActor(final AmqpConnection amqpConnection, final ActorRef commandProcessor) {
        this.amqpConnection = amqpConnection;
        this.commandProcessor = commandProcessor;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @return the Akka configuration Props object
     */
    public static Props props(final AmqpConnection amqpConnection, final ActorRef commandProcessor) {
        return Props.create(RabbitMQClientActor.class, new Creator<RabbitMQClientActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public RabbitMQClientActor create() {
                return new RabbitMQClientActor(amqpConnection, commandProcessor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateConnection.class, cc -> {
                    amqpConnection = cc.getAmqpConnection();
                    connect();
                })
                .match(OpenConnection.class, oc -> connect())
                .match(CloseConnection.class, cc -> disconnect())
                .match(DeleteConnection.class, dc -> {
                    disconnect();
                    stopSelf();
                })
                .match(ChannelCreated.class, this::handleChannelCreated)
                .build();
    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        this.channelActor = channelCreated.channel();
        setupConsumers(this.channelActor);
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

    private void disconnect() {
        amqpConnection.getSources().forEach(source -> stopChildActor("consumer-" + source));
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

    private void setupConsumers(final ActorRef channelActor) {
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

// TODO DG how to do acking
//    private void handleAck(final InternalMessage.Ack<Long> ack) {
//        channelActor.tell(ChannelMessage.apply(channel -> {
//            try {
//                channel.basicAck(ack.getMessage(), false);
//            } catch (IOException e) {
//                log.info("ACKing {} failed: ", e.getMessage());
//            }
//            return null;
//        }, false), self());
//    }

    private ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        return getContext().actorOf(props, nameEscaped);
    }

    private void stopChildActor(final String name) {
        log.debug("Stopping child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        getContext().findChild(nameEscaped).ifPresent(getContext()::stop);
    }

    private void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    private void stopSelf() {
        log.debug("Shutting down");
        getContext().stop(self());
    }

    class RabbitMQMessageConsumer extends DefaultConsumer {

        private final ActorRef commandConsumer;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        RabbitMQMessageConsumer(final ActorRef commandConsumer, final Channel channel) {
            super(channel);
            this.commandConsumer = commandConsumer;
        }

        @Override
        public void handleDelivery(final String consumerTag, final Envelope envelope,
                final AMQP.BasicProperties properties, final byte[] body) {
            commandConsumer.tell(new Delivery(envelope, properties, body), RabbitMQClientActor.this.self());
        }

    }
}