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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import com.newmotion.akka.rabbitmq.ChannelActor;
import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.CreateChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.DefaultExceptionHandler;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor which handles connection to AMQP 0.9.1 server.
 */
public final class RabbitMQClientActor extends BaseClientActor {

    private static final String RMQ_CONNECTION_ACTOR_NAME = "rmq-connection";
    private static final String CONSUMER_CHANNEL = "consumer-channel";
    private static final String PUBLISHER_CHANNEL = "publisher-channel";
    private static final String CONSUMER_ACTOR_PREFIX = "consumer-";

    private final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory;
    @Nullable private ActorRef rmqConnectionActor;
    @Nullable private ActorRef consumerChannelActor;
    @Nullable private ActorRef rmqPublisherActor;
    @Nullable private ActorRef createConnectionSender;

    private final Map<String, String> consumedTagsToAddresses;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    private RabbitMQClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory,
            final ActorRef conciergeForwarder) {
        super(connection, connectionStatus, conciergeForwarder);

        this.rabbitConnectionFactoryFactory = rabbitConnectionFactoryFactory;
        consumedTagsToAddresses = new HashMap<>();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    private RabbitMQClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder) {
        this(connection, connectionStatus, ConnectionBasedRabbitConnectionFactoryFactory.getInstance(),
                conciergeForwarder);

    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {
        return Props.create(RabbitMQClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param connectionStatus the desired status of the.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param rabbitConnectionFactoryFactory the ConnectionFactory Factory to use.
     * @return the Akka configuration Props object.
     */
    public static Props propsForTests(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder, final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory) {
        return Props.create(RabbitMQClientActor.class, validateConnection(connection), connectionStatus,
                rabbitConnectionFactoryFactory, conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        // the target addresses must have the format exchange/routingKey for RabbitMQ
        connection.getTargets()
                .stream()
                .map(Target::getAddress)
                .forEach(RabbitMQTarget::fromTargetAddress);
        return connection;
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
        return connect(connection, getSender());
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        createConnectionSender = origin;
        connect(connection, origin)
                .thenAccept(status -> log.info("Status of connecting in doConnectClient: {}", status));
    }

    @Override
    protected void doReconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        stopCommandConsumers();
        stopCommandPublisher();

        onClientDisconnected(Optional::empty, stateData());

        createConnectionSender = origin;

        // wait a little until connecting again:
        getContext().getSystem().scheduler().scheduleOnce(FiniteDuration.apply(500, TimeUnit.MILLISECONDS),
                () -> connect(connection, origin)
                        .thenAccept(status -> {
                            log.info("Reconnected successfully");
                        }), getContext().getSystem().dispatcher());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        stopCommandConsumers();
        stopCommandPublisher();
        getSelf().tell((ClientDisconnected) () -> Optional.ofNullable(origin), origin);
    }

    @Override
    protected void onClientConnected(final ClientConnected clientConnected, final BaseClientData data) {
        log.info("Received ClientConnected");
        if (rmqConnectionActor != null) {
            if (consumerChannelActor == null) {
                // create a consumer channel - if source is configured
                if (isConsuming()) {
                    rmqConnectionActor.tell(
                            CreateChannel.apply(
                                    ChannelActor.props((channel, actorRef) -> {
                                        log.info("Did set up consumer channel: {}", channel);
                                        startCommandConsumers(channel);
                                        consumerChannelActor = actorRef;
                                        return null;
                                    }),
                                    Option.apply(CONSUMER_CHANNEL)), getSelf());
                } else {
                    log.info("Not starting channels, no sources were configured");
                }
            } else {
                log.info("Consumer is already created, didn't create it again..");
            }
            log.debug("Connection '{}' opened.", connectionId());
        }
    }

    @Override
    protected void onClientDisconnected(final ClientDisconnected clientDisconnected, final BaseClientData data) {
        log.info("Received ClientDisconnected");
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
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(rmqPublisherActor);
    }

    @Override
    protected void onConnectionFailure(final ConnectionFailure connectionFailure, final BaseClientData data) {
        super.onConnectionFailure(connectionFailure, data);

        final Throwable exception = connectionFailure.getFailure().cause();
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        log.warning("Got unexpected ConnectionDriver exception on connection <{}> {}: {}", connectionId(),
                exception.getClass().getSimpleName(), exception.getMessage());
        if (createConnectionSender != null) {
            createConnectionSender.tell(
                    ConnectionFailedException.newBuilder(connectionId())
                            .description("The requested Connection could not be connected due to '" +
                                    exception.getClass().getSimpleName() + ": " + exception.getMessage() + "'")
                            .cause(exception)
                            .build(), null);
            createConnectionSender = null;
        }
    }

    @Override
    protected Map<String, AddressMetric> getSourceConnectionStatus(final Source source) {
        try {
            return collectAsList(source.getAddresses().stream()
                    .flatMap(address -> IntStream.range(0, source.getConsumerCount())
                            .mapToObj(idx -> {
                                final String addressWithIndex = address + "-" + idx;
                                final String actorName = escapeActorName(CONSUMER_ACTOR_PREFIX + addressWithIndex);
                                return retrieveAddressMetric(addressWithIndex, actorName);
                            })
                    ).collect(Collectors.toList()))
                    .thenApply((entries) ->
                            entries.stream().collect(Collectors.toMap(Pair::first, Pair::second)))
                    .get(RETRIEVE_METRICS_TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e, "Error while aggregating sources ConnectionStatus: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    protected Map<String, AddressMetric> getTargetConnectionStatus(final Target target) {
        final HashMap<String, AddressMetric> targetStatus = new HashMap<>();
        try {
            final Pair<String, AddressMetric> targetEntry =
                    retrieveAddressMetric(target.getAddress(), RabbitMQPublisherActor.ACTOR_NAME)
                            .get(RETRIEVE_METRICS_TIMEOUT, TimeUnit.SECONDS);
            targetStatus.put(targetEntry.first(), targetEntry.second());
            return targetStatus;
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e, "Error while aggregating target ConnectionStatus: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void handleChannelCreated(final ChannelCreated channelCreated) {
        consumerChannelActor = channelCreated.channel();
    }

    private CompletionStage<Status.Status> connect(final Connection connection, @Nullable final ActorRef origin) {
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        if (rmqConnectionActor == null) {
            final ActorRef self = getSelf();
            try {
                final ConnectionFactory connectionFactory = rabbitConnectionFactoryFactory
                        .createConnectionFactory(connection, new RabbitMQExceptionHandler(throwable -> {
                            self.tell(new ImmutableConnectionFailure(origin, throwable, null), self);
                            future.complete(new Status.Failure(throwable));
                        }));

                final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                        FiniteDuration.apply(10, TimeUnit.SECONDS), (rmqConnection, connectionActorRef) -> {
                            log.info("Established RMQ connection: {}", rmqConnection);
                            self.tell((ClientConnected) () -> Optional.ofNullable(createConnectionSender), origin);
                            return null;
                        });
                rmqConnectionActor = startChildActor(RMQ_CONNECTION_ACTOR_NAME, props);

                rmqPublisherActor = startRmqPublisherActor().orElse(null);

                // create publisher channel
                rmqConnectionActor.tell(
                        CreateChannel.apply(
                                ChannelActor.props((channel, s) -> {
                                    log.info("Did set up publisher channel: {}", channel);
                                    future.complete(new Status.Success("channel created"));
                                    return null;
                                }),
                                Option.apply(PUBLISHER_CHANNEL)), rmqPublisherActor);
            } catch (final Exception exception) {
                self.tell(new ImmutableConnectionFailure(origin, exception, null), self);
                future.complete(new Status.Failure(exception));
            }
        } else {
            log.debug("Connection '{}' is already open.", connectionId());
            future.complete(new Status.Success("already connected"));
        }
        return future;
    }

    private Optional<ActorRef> startRmqPublisherActor() {
        if (isPublishing()) {
            return Optional.of(getContext().findChild(RabbitMQPublisherActor.ACTOR_NAME).orElseGet(() -> {
                final Props publisherProps = RabbitMQPublisherActor.props(getTargetsOrEmptySet());
                return startChildActor(RabbitMQPublisherActor.ACTOR_NAME, publisherProps);
            }));
        } else {
            return Optional.empty();
        }
    }

    private void stopCommandPublisher() {
        stopChildActor(RabbitMQPublisherActor.ACTOR_NAME);
    }

    private void stopCommandConsumers() {
        getContext().getChildren().forEach(child -> {
            final String actorName = child.path().name();
            if (actorName.startsWith(CONSUMER_ACTOR_PREFIX)) {
                stopChildActor(child);
            }
        });

        // block until all were stopped:
        getContext().getChildren().forEach(child -> {
            final String actorName = child.path().name();
            if (actorName.startsWith(CONSUMER_ACTOR_PREFIX)) {
                int counter = 5;
                while (getContext().findChild(actorName).isPresent()) {
                    try {
                        Thread.sleep(10);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (--counter == 0) {
                        break;
                    }
                }
                ;
            }
        });
    }

    private void startCommandConsumers(final Channel channel) {
        log.info("Starting to consume queues...");
        try {
            ensureQueuesExist(channel);
            stopCommandConsumers();
            startConsumers(channel);
        } catch (final DittoRuntimeException dre) {
            if (createConnectionSender != null) {
                createConnectionSender.tell(new Status.Failure(dre), getSelf());
                createConnectionSender = null;
            }
            if (consumerChannelActor != null) {
                // stop consumer channel actor
                stopChildActor(consumerChannelActor);
                consumerChannelActor = null;
            }
        }
        if (createConnectionSender != null) {
            createConnectionSender.tell(new Status.Success(BaseClientState.CONNECTED), getSelf());
            createConnectionSender = null;
        }
    }

    private void startConsumers(final Channel channel) {
        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessorActor();
        if (messageMappingProcessor.isPresent()) {
            getSourcesOrEmptySet().forEach(source ->
                    source.getAddresses().forEach(sourceAddress -> {
                        for (int i = 0; i < source.getConsumerCount(); i++) {
                            final String addressWithIndex = sourceAddress + "-" + i;
                            final String childName = CONSUMER_ACTOR_PREFIX + addressWithIndex;
                            final ActorRef consumer = startChildActor(childName,
                                    RabbitMQConsumerActor.props(sourceAddress, messageMappingProcessor.get()));
                            try {
                                final String consumerTag = channel.basicConsume(sourceAddress, false,
                                        new RabbitMQMessageConsumer(consumer, channel));
                                log.debug("Consuming queue <{}>, consumer tag is <{}>", addressWithIndex, consumerTag);
                                consumedTagsToAddresses.put(consumerTag, addressWithIndex);
                            } catch (final IOException e) {
                                log.warning("Failed to consume queue <{}>: <{}>", addressWithIndex, e.getMessage());
                            }
                        }
                    })
            );
        } else {
            log.warning("The MessageMappingProcessor was not available and therefore no consumers were started!");
        }
    }

    private void ensureQueuesExist(final Channel channel) {
        final List<String> missingQueues = new ArrayList<>();
        getSourcesOrEmptySet().forEach(consumer ->
                consumer.getAddresses().forEach(address -> {
                    try {
                        channel.queueDeclarePassive(address);
                    } catch (final IOException e) {
                        missingQueues.add(address);
                        log.warning("The queue <{}> does not exist.", address);
                    }
                })
        );
        if (!missingQueues.isEmpty()) {
            log.warning("Stopping RMQ client actor for connection <{}> as queues to connect to are missing: <{}>",
                    connectionId(), missingQueues);
            throw ConnectionFailedException.newBuilder(connectionId())
                    .description("The queues " + missingQueues + " to connect to are missing.")
                    .build();
        }
    }

    private Optional<String> consumingQueueByTag(final String consumerTag) {
        return Optional.ofNullable(consumedTagsToAddresses.get(consumerTag));
    }

    /**
     * Custom exception handler which handles exception during connection.
     */
    private class RabbitMQExceptionHandler extends DefaultExceptionHandler {

        private final Consumer<Throwable> exceptionHandler;

        private RabbitMQExceptionHandler(final Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void handleUnexpectedConnectionDriverException(final com.rabbitmq.client.Connection conn,
                final Throwable exception) {

            // establishing the connection was not possible (maybe wrong host, port, credentials, ...)
            exceptionHandler.accept(exception);
        }
    }

    /**
     * Custom consumer which is notified about different events related to the connection in order to track
     * connectivity status.
     */
    private class RabbitMQMessageConsumer extends DefaultConsumer {

        private final ActorRef consumerActor;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        private RabbitMQMessageConsumer(final ActorRef consumerActor, final Channel channel) {
            super(channel);
            this.consumerActor = consumerActor;
            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN,
                    "Consumer initialized at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleDelivery(final String consumerTag, final Envelope envelope,
                final AMQP.BasicProperties properties, final byte[] body) {

            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            try {
                consumerActor.tell(new Delivery(envelope, properties, body), RabbitMQClientActor.this.getSelf());
            } catch (final Exception e) {
                log.info("Failed to process delivery <{}>: {}", envelope.getDeliveryTag(), e.getMessage());
            } finally {
                incrementConsumedMessageCounter();
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
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            consumingQueueByTag(consumerTag).ifPresent(queueName -> {
                log.info("consume OK for consumer queue <{}> " + "on connection <{}>", queueName, connectionId());
            });

            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN,
                    "Consumer started at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleCancel(final String consumerTag) throws IOException {
            super.handleCancel(consumerTag);
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            consumingQueueByTag(consumerTag).ifPresent(queueName -> {
                log.warning("Consumer with queue <{}> was cancelled on connection <{}> - this can happen for " +
                        "example when the queue was deleted", queueName, connectionId());
            });

            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                    "Consumer for queue cancelled at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
            super.handleShutdownSignal(consumerTag, sig);
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            consumingQueueByTag(consumerTag).ifPresent(queueName -> {
                log.warning("Consumer with queue <{}> shutdown as the channel or the underlying connection has " +
                        "been shut down on connection <{}>", queueName, connectionId());
            });

            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                    "Channel or the underlying connection has been shut down at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleRecoverOk(final String consumerTag) {
            super.handleRecoverOk(consumerTag);
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            log.info("recovered OK for consumer with tag <{}> " + "on connection <{}>", consumerTag, connectionId());

            getSelf().tell((ClientConnected) Optional::empty, getSelf());
        }

    }

}
