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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ConnectivityModifyCommand;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

import com.newmotion.akka.rabbitmq.ChannelActor;
import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.newmotion.akka.rabbitmq.ConnectionActor;
import com.newmotion.akka.rabbitmq.CreateChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.Option;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor which handles connection to AMQP 0.9.1 server.
 */
public final class RabbitMQClientActor extends BaseClientActor {

    private static final String RMQ_CONNECTION_PREFIX = "rmq-connection-";
    private static final String RMQ_PUBLISHER_PREFIX = "rmq-publisher-";
    private static final String CONSUMER_CHANNEL = "consumer-channel";
    private static final String PUBLISHER_CHANNEL = "publisher-channel";
    private static final String CONSUMER_ACTOR_PREFIX = "consumer-";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable private ActorRef rmqConnectionActor;
    @Nullable private ActorRef consumerChannelActor;
    @Nullable private ActorRef rmqPublisherActor;
    @Nullable private ActorRef createConnectionSender;

    private RabbitMQClientActor(final String connectionId, final ActorRef rmqConnectionActor) {
        super(connectionId, rmqConnectionActor, ConnectivityMessagingConstants.GATEWAY_PROXY_ACTOR_PATH);
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
                .match(TestConnection.class, this::handleTest)
                .match(CreateConnection.class, this::handleConnect)
                .match(OpenConnection.class, this::handleOpenConnection)
                .match(CloseConnection.class, this::handleDisconnect)
                .match(DeleteConnection.class, this::handleDisconnect)
                .match(ChannelCreated.class, this::handleChannelCreated)
                .match(Signal.class, this::handleSignal)
                .match(RetrieveConnectionMetrics.class, this::handleRetrieveMetrics)
                .build()
                .orElse(initHandling);
    }

    private void handleSignal(final Signal<?> signal) {
        if (messageMappingProcessor != null) {
            messageMappingProcessor.tell(signal, getSelf());
        }
    }

    private void handleTest(final TestConnection test) {
        connection = test.getConnection();
        testConnection = true;
        mappingContexts = test.getMappingContexts();

        // reset receive timeout when TestConnection is received
        getContext().setReceiveTimeout(Duration.Undefined());

        createConnectionSender = getSender();

        connect();
    }

    private void handleConnect(final CreateConnection connect) {
        connection = connect.getConnection();
        mappingContexts = connect.getMappingContexts();

        // reset receive timeout when CreateConnection is received
        getContext().setReceiveTimeout(Duration.Undefined());

        createConnectionSender = getSender();

        connect();
    }

    private void handleOpenConnection(final OpenConnection openConnection) {
        createConnectionSender = getSender();
        openConnection();
    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        this.consumerChannelActor = channelCreated.channel();
        startCommandConsumers();
    }

    private void handleRetrieveMetrics(final RetrieveConnectionMetrics command) {
        final ActorRef origin = getSender();
        PatternsCS.ask(rmqConnectionActor, ConnectionActor.GetState$.MODULE$, Timeout.apply(1, TimeUnit.SECONDS))
                .whenComplete((response, exception) -> {
                    log.debug("Got response to {}: {}", command.getType(), exception == null ? response : exception);
                    if (response != null) {
                        final ConnectionStatus status = response.equals(ConnectionActor.Connected$.MODULE$) ?
                                ConnectionStatus.OPEN : ConnectionStatus.CLOSED;
                        origin.tell(RetrieveConnectionMetricsResponse.of(
                                connectionId, status, command.getDittoHeaders()), null);
                    } else {
                        origin.tell(RetrieveConnectionMetricsResponse.of(
                                connectionId, ConnectionStatus.FAILED, command.getDittoHeaders()), null);
                    }
                });
    }

    private void connect() {
        if (rmqConnectionActor == null && connection != null) {
            final ConnectionFactory connectionFactory =
                    ConnectionBasedRabbitConnectionFactory.createConnection(connection, createConnectionSender);

            final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                    FiniteDuration.apply(10, TimeUnit.SECONDS), (rmqConnection, connectionActorRef) -> {
                        log.info("Established RMQ connection: {}", rmqConnection);
                        return null;
                    });
            rmqConnectionActor = startChildActor(RMQ_CONNECTION_PREFIX + connectionId, props);

            final Props publisherProps = RabbitMQPublisherActor.props(connection);
            rmqPublisherActor =
                    startChildActor(RabbitMQPublisherActor.ACTOR_NAME_PREFIX + connectionId, publisherProps);

            startMessageMappingProcessor(rmqPublisherActor);

            // create publisher channel
            rmqConnectionActor.tell(
                    CreateChannel.apply(
                            ChannelActor.props((channel, s) -> null),
                            Option.apply(PUBLISHER_CHANNEL)), rmqPublisherActor);

            openConnection();
        } else {
            log.debug("Connection '{}' is already open.", connectionId);
        }
    }

    private void openConnection() {
        if (rmqConnectionActor != null) {
            // create a consumer channel - if source is configured
            if (isConsuming()) {
                rmqConnectionActor.tell(
                        CreateChannel.apply(
                                ChannelActor.props((channel, s) -> null),
                                Option.apply(CONSUMER_CHANNEL)), getSelf());
            }
            log.debug("Connection '{}' opened.", connectionId);
        }
    }

    private void handleDisconnect(final ConnectivityModifyCommand<?> cmd) {
        log.debug("Handling <{}> command: {}", cmd.getType(), cmd);
        stopCommandConsumers();
        stopMessageMappingProcessor();
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
        getSender().tell(new Status.Success("disconnected"), getSelf());
    }

    private void stopCommandPublisher() {
        stopChildActor(RMQ_PUBLISHER_PREFIX + connectionId);
    }

    private void stopCommandConsumers() {
        getContext().getChildren().forEach(child -> {
            if (child.path().name().startsWith(CONSUMER_ACTOR_PREFIX)) {
                getContext().stop(child);
            }
        });
    }

    private void startCommandConsumers() {
        log.info("Channel created, start to consume queues...");
        if (consumerChannelActor == null) {
            log.info("No consumerChannelActor, cannot consume queues without a channel.");
        } else {
            final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
                try {
                    ensureQueuesExist(channel);
                    startConsumers(channel);
                } catch (final DittoRuntimeException dre) {
                    if (createConnectionSender != null) {
                        createConnectionSender.tell(new Status.Failure(dre), getSelf());
                        createConnectionSender = null;
                    }
                    // stop consumer channel actor
                    stopChildActor(consumerChannelActor);
                    consumerChannelActor = null;
                }
                if (createConnectionSender != null) {
                    createConnectionSender.tell(new Status.Success("connected"), getSelf());
                    createConnectionSender = null;
                }
                return null;
            }, false);

            consumerChannelActor.tell(channelMessage, getSelf());
        }
    }

    private void startConsumers(final Channel channel) {
        if (!testConnection) {
            if (messageMappingProcessor != null) {
                getSourcesOrEmptySet().forEach(consumer -> {
                    consumer.getSources().forEach(source -> {
                        for (int i = 0; i < consumer.getConsumerCount(); i++) {
                            final ActorRef commandConsumer = startChildActor(CONSUMER_ACTOR_PREFIX + source + "-" + i,
                                    RabbitMQConsumerActor.props(messageMappingProcessor));
                            try {
                                final String consumerTag = channel.basicConsume(source, false,
                                        new RabbitMQMessageConsumer(commandConsumer, channel));
                                log.debug("Consuming queue {}, consumer tag is {}", consumer, consumerTag);
                            } catch (final IOException e) {
                                log.warning("Failed to consume queue '{}': {}", consumer, e.getMessage());
                            }
                        }
                    });
                });
            } else {
                log.error("The messageMappingProcessor was null and therefore no consumers were started!");
            }
        } else {
            log.info("Not starting consumer on channel <{}> as this is only a test connection", channel);
        }
    }

    private void ensureQueuesExist(final Channel channel) {
        final List<String> missingQueues = new ArrayList<>();
        getSourcesOrEmptySet().forEach(consumer -> {
            consumer.getSources().forEach(source -> {
                try {
                    channel.queueDeclarePassive(source);
                } catch (final IOException e) {
                    missingQueues.add(source);
                    log.warning("The queue <{}> does not exist.", source);
                }
            });
        });
        if (!missingQueues.isEmpty()) {
            log.error("Stopping RMQ client actor for connection <{}> as queues to connect to are missing: <{}>",
                    connectionId, missingQueues);
            throw ConnectionFailedException.newBuilder(connectionId)
                    .description("The queues " + missingQueues + " to connect to are missing.")
                    .build();
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
                commandConsumer.tell(new Delivery(envelope, properties, body), RabbitMQClientActor.this.getSelf());
            } catch (final Exception e) {
                log.info("Failed to process delivery <{}>: {}", envelope.getDeliveryTag(), e.getMessage());
            } finally {
                try {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                } catch (final IOException e) {
                    log.info("Failed to ack delivery <{}>: {}", envelope.getDeliveryTag(), e.getMessage());
                }
            }
        }

        @Override
        public void handleCancel(final String consumerTag) throws IOException {
            log.warning("Consumer with tag <{}> was cancelled on connection <{}> - this can happen for example when " +
                    "the queue was deleted", consumerTag, connectionId);
            // TODO TJ handle cancelations
        }

        @Override
        public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
            log.warning("the channel or the underlying connection has been shut down for consumer with tag <{}> " +
                            "on connection <{}>", consumerTag, connectionId);
            // TODO TJ handle shutdowns
        }
    }
}
