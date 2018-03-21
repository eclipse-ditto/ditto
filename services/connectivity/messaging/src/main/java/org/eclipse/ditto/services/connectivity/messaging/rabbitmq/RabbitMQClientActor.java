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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.ConnectivityModifyCommand;

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
import com.rabbitmq.client.ShutdownSignalException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.FSMStateFunctionBuilder;
import scala.Option;
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

    private final Map<String, QueueConsumption> consumedQueues; // TODO TJ use it or get rid of it again

    private RabbitMQClientActor(final String connectionId, final ActorRef rmqConnectionActor) {
        super(connectionId, null, rmqConnectionActor, ConnectivityMessagingConstants.GATEWAY_PROXY_ACTOR_PATH);

        consumedQueues = new HashMap<>();
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
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> unhandledHandler(final String connectionId) {
        return super.unhandledHandler(connectionId)
                .event(ChannelCreated.class, BaseClientData.class, (channelCreated, data) -> {
                    handleChannelCreated(channelCreated);
                    return stay();
                });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {

        createConnectionSender = getSender();

        return connect(connection);
    }

    @Override
    protected void onClientConnected(final ClientConnected clientConnected, final BaseClientData data) {
        openConnection();
        startMessageMappingProcessor(rmqPublisherActor, data.getMappingContexts());
    }

    @Override
    protected void onClientDisconnected(final ClientDisconnected clientDisconnected, final BaseClientData data) {
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
    }

    @Override
    protected void doConnectClient(final Connection connection) {

        createConnectionSender = getSender();

        connect(connection); // TODO TJ what to do with result?
    }

    @Override
    protected void doDisconnectClient(final Connection connection) {

    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        this.consumerChannelActor = channelCreated.channel();
        startCommandConsumers();
    }

    private void retrieveQueueStatus() {
        consumedQueues.forEach((queueName, value) -> {
            final boolean consuming = value.consuming;
            final String details = value.details;
            log.info("Queue <{}> is consuming: <{}>, details: <{}>", queueName, consuming, details);
        });
    }

    private CompletionStage<Status.Status> connect(final Connection connection) {

        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        if (rmqConnectionActor == null) {
            final ConnectionFactory connectionFactory =
                    ConnectionBasedRabbitConnectionFactory.createConnection(connection, createConnectionSender);

            final ActorRef self = getSelf();
            final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                    FiniteDuration.apply(10, TimeUnit.SECONDS), (rmqConnection, connectionActorRef) -> {
                        log.info("Established RMQ connection: {}", rmqConnection);
                        self.tell((ClientConnected) () -> createConnectionSender, getSelf());
                        return null;
                    });
            rmqConnectionActor = startChildActor(RMQ_CONNECTION_PREFIX + connectionId(), props);

            final Props publisherProps = RabbitMQPublisherActor.props(connection);
            rmqPublisherActor =
                    startChildActor(RabbitMQPublisherActor.ACTOR_NAME_PREFIX + connectionId(), publisherProps);

            // create publisher channel
            rmqConnectionActor.tell(
                    CreateChannel.apply(
                            ChannelActor.props((channel, s) -> {
                                log.info("Did set up channel: {}", channel);
                                future.complete(new Status.Success("channel created"));
                                return null;
                            }),
                            Option.apply(PUBLISHER_CHANNEL)), rmqPublisherActor);
        } else {
            log.debug("Connection '{}' is already open.", connectionId());
            future.complete(new Status.Success("already connected"));
        }
        return future;
    }

    private void openConnection() {
        if (rmqConnectionActor != null) {
            if (consumerChannelActor == null) {
                // create a consumer channel - if source is configured
                if (isConsuming()) {
                    rmqConnectionActor.tell(
                            CreateChannel.apply(
                                    ChannelActor.props((channel, s) -> null),
                                    Option.apply(CONSUMER_CHANNEL)), getSelf());
                }
            } else {
                log.info("Consumer is already created, don't created it again..");
            }
            log.debug("Connection '{}' opened.", connectionId());
        }
    }

    private void handleDisconnect(final ConnectivityModifyCommand<?> cmd) {

    }

    private void stopCommandPublisher() {
        stopChildActor(RMQ_PUBLISHER_PREFIX + connectionId());
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

        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessor();
        if (messageMappingProcessor.isPresent()) {
            getSourcesOrEmptySet().forEach(consumer -> {
                consumer.getSources().forEach(source -> {
                    for (int i = 0; i < consumer.getConsumerCount(); i++) {
                        final ActorRef commandConsumer = startChildActor(CONSUMER_ACTOR_PREFIX + source + "-" + i,
                                RabbitMQConsumerActor.props(messageMappingProcessor.get()));
                        try {
                            final String consumerTag =
                                    channel.basicConsume(source, false,
                                            new RabbitMQMessageConsumer(commandConsumer, channel));
                            log.debug("Consuming queue {}, consumer tag is {}", consumer, consumerTag);
                            consumedQueues.put(source, new QueueConsumption(consumerTag, true,
                                    "Consumer started at " + Instant.now()));
                        } catch (final IOException e) {
                            log.warning("Failed to consume queue '{}': {}", consumer, e.getMessage());
                        }
                    }
                });
            });
        } else {
            log.warning("The MessageMappingProcessor was not available and therefore no consumers were started!");
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
            log.warning("Stopping RMQ client actor for connection <{}> as queues to connect to are missing: <{}>",
                    connectionId(), missingQueues);
            throw ConnectionFailedException.newBuilder(connectionId())
                    .description("The queues " + missingQueues + " to connect to are missing.")
                    .build();
        }
    }

    private Optional<Map.Entry<String, QueueConsumption>> consumingQueueByTag(final String consumerTag) {
        return consumedQueues.entrySet().stream()
                .filter(e -> consumerTag.equalsIgnoreCase(e.getValue().consumerTag))
                .findFirst();
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
        public void handleConsumeOk(final String consumerTag) {
            super.handleConsumeOk(consumerTag);

            log.info("consume OK for consumer with tag <{}> " + "on connection <{}>", consumerTag, connectionId());
            consumingQueueByTag(consumerTag).ifPresent(entry -> {
                final String queueName = entry.getKey();
                consumedQueues.put(queueName, new QueueConsumption(entry.getValue().consumerTag, true,
                        "Consumer started at " + Instant.now()));
            });
        }

        @Override
        public void handleCancel(final String consumerTag) throws IOException {
            super.handleCancel(consumerTag);
            log.warning("Consumer with tag <{}> was cancelled on connection <{}> - this can happen for example when " +
                    "the queue was deleted", consumerTag, connectionId());

            getSelf().tell(new RmqFailure(ActorRef.noSender(), null,
                    "Consumer canceled, probably due to deleted queue"), getSelf());

            consumingQueueByTag(consumerTag).ifPresent(entry -> {
                final String queueName = entry.getKey();
                consumedQueues.put(queueName, new QueueConsumption(entry.getValue().consumerTag, false,
                        "Consumer for queue cancelled at " + Instant.now()));
            });
        }

        @Override
        public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
            super.handleShutdownSignal(consumerTag, sig);
            log.warning("the channel or the underlying connection has been shut down for consumer with tag <{}> " +
                    "on connection <{}>", consumerTag, connectionId());

            getSelf().tell(new RmqFailure(ActorRef.noSender(), sig,
                    "Channel or the underlying connection has been shut down"), getSelf());

            consumingQueueByTag(consumerTag).ifPresent(entry -> {
                final String queueName = entry.getKey();
                consumedQueues.put(queueName, new QueueConsumption(entry.getValue().consumerTag, false,
                        "Channel shutdown at " + Instant.now()));
            });
        }

        @Override
        public void handleRecoverOk(final String consumerTag) {
            super.handleRecoverOk(consumerTag);

            log.info("recovered OK for consumer with tag <{}> " + "on connection <{}>", consumerTag, connectionId());

            getSelf().tell((ClientConnected) ActorRef::noSender, getSelf());
        }

    }

    private class QueueConsumption {

        private final String consumerTag;
        private final boolean consuming;
        private final String details;

        private QueueConsumption(final String consumerTag, final boolean consuming, final String details) {
            this.consumerTag = consumerTag;
            this.consuming = consuming;
            this.details = details;
        }
    }

    /**
     * {@code Failure} message for RMQ.
     */
    static class RmqFailure implements ConnectionFailure {

        private final ActorRef origin;
        @Nullable private final Throwable cause;
        @Nullable private final String description;
        private final Instant time;

        RmqFailure(final ActorRef origin, @Nullable final Throwable cause, @Nullable final String description) {
            this.origin = origin;
            this.cause = cause;
            this.description = description;
            time = Instant.now();
        }

        @Override
        public Status.Failure getFailure() {
            return new Status.Failure(cause);
        }

        @Override
        public String getFailureDescription() {
            String responseStr = "";
            if (cause != null) {
                if (description != null) {
                    responseStr = description + " - cause ";
                }
                responseStr += cause.getClass().getSimpleName() + ": " + cause.getMessage();
                if (cause instanceof DittoRuntimeException) {
                    responseStr += " / " + ((DittoRuntimeException) cause).getDescription().orElse("");
                }
            } else if (description != null) {
                responseStr = description;
            } else {
                responseStr = "unknown failure";
            }
            responseStr += " at " + time;
            return responseStr;
        }

        @Override
        public ActorRef getOrigin() {
            return origin;
        }
    }
}
