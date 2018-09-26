/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
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
import akka.japi.pf.FI;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.PatternsCS;
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
    @Nullable private ActorRef rmqPublisherActor;

    private final Map<String, String> consumedTagsToAddresses;
    private final Map<String, ActorRef> consumerByAddressWithIndex;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private RabbitMQClientActor(final Connection connection,
            final ConnectionStatus connectionStatus,
            final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory,
            final ActorRef conciergeForwarder) {

        super(connection, connectionStatus, conciergeForwarder);

        this.rabbitConnectionFactoryFactory = rabbitConnectionFactoryFactory;
        consumedTagsToAddresses = new HashMap<>();
        consumerByAddressWithIndex = new HashMap<>();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    @SuppressWarnings("unused")
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
    public static Props propsForTests(final Connection connection,
            final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder,
            final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory) {

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
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return super.inConnectedState()
                .event(ClientConnected.class, BaseClientData.class,
                        (event, data) -> {
                            // when connection is lost, the library (ChannelActor) will automatically reconnect
                            // without the state of this actor changing. But we will receive a new ClientConnected message
                            // that we can use to bind our consumers to the channels.
                            this.allocateResourcesOnConnection(event);
                            return stay();
                        });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        return connect(connection, FiniteDuration.apply(TEST_CONNECTION_TIMEOUT, TimeUnit.SECONDS));
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final boolean consuming = isConsuming();
        final ActorRef self = getSelf();
        connect(connection, FiniteDuration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS))
                .thenAccept(status -> createConsumerChannelAndNotifySelf(status, consuming, rmqConnectionActor, self));
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        getSelf().tell((ClientDisconnected) () -> Optional.ofNullable(origin), origin);
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        log.debug("Received ClientConnected");
        if (clientConnected instanceof RmqConsumerChannelCreated) {
            final RmqConsumerChannelCreated rmqConsumerChannelCreated = (RmqConsumerChannelCreated) clientConnected;
            startCommandConsumers(rmqConsumerChannelCreated.getChannel());
        }
    }

    @Override
    protected void cleanupResourcesForConnection() {
        log.debug("cleaning up");
        stopCommandConsumers();
        stopCommandPublisher();
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
    protected CompletionStage<Map<String, AddressMetric>> getSourceConnectionStatus(final Source source) {
        return collectAsList(source.getAddresses().stream()
                .flatMap(address -> IntStream.range(0, source.getConsumerCount())
                        .mapToObj(idx -> {
                            final String addressWithIndex = address + "-" + idx;
                            final String actorLabel = CONSUMER_ACTOR_PREFIX + addressWithIndex;
                            final ActorRef consumer = consumerByAddressWithIndex.get(addressWithIndex);
                            return retrieveAddressMetric(addressWithIndex, actorLabel, consumer);
                        })
                ))
                .thenApply(entries -> entries.stream().collect(Collectors.toMap(Pair::first, Pair::second)));
    }

    @Override
    protected CompletionStage<Map<String, AddressMetric>> getTargetConnectionStatus(final Target target) {
        final CompletionStage<Pair<String, AddressMetric>> targetEntryFuture =
                retrieveAddressMetric(target.getAddress(), RabbitMQPublisherActor.ACTOR_NAME, rmqPublisherActor);

        return targetEntryFuture.thenApply(targetEntry ->
                Collections.singletonMap(targetEntry.first(), targetEntry.second()));
    }

    private static Optional<ConnectionFactory> tryToCreateConnectionFactory(
            final RabbitConnectionFactoryFactory factoryFactory,
            final Connection connection,
            final RabbitMQExceptionHandler rabbitMQExceptionHandler) {

        try {
            return Optional.of(factoryFactory.createConnectionFactory(connection, rabbitMQExceptionHandler));
        } catch (final Throwable throwable) {
            // error creating factory; return early.)
            rabbitMQExceptionHandler.exceptionHandler.accept(throwable);
            return Optional.empty();
        }
    }

    private static Object messageFromConnectionStatus(final Status.Status status) {
        if (status instanceof Status.Failure) {
            final Status.Failure failure = (Status.Failure) status;
            return new ImmutableConnectionFailure(null, failure.cause(), null);
        } else {
            return (ClientConnected) Optional::empty;
        }
    }

    private CompletionStage<Status.Status> connect(final Connection connection, final FiniteDuration timeout) {

        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        if (rmqConnectionActor == null) {
            // complete the future if something went wrong during creation of the connection-factory-factory
            final RabbitMQExceptionHandler rabbitMQExceptionHandler =
                    new RabbitMQExceptionHandler(throwable -> future.complete(new Status.Failure(throwable)));

            final Optional<ConnectionFactory> connectionFactoryOpt =
                    tryToCreateConnectionFactory(rabbitConnectionFactoryFactory, connection, rabbitMQExceptionHandler);

            if (connectionFactoryOpt.isPresent()) {
                final ConnectionFactory connectionFactory = connectionFactoryOpt.get();

                final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                        timeout, (rmqConnection, connectionActorRef) -> {
                            log.info("Established RMQ connection: {}", rmqConnection);
                            return null;
                        });
                rmqConnectionActor = startChildActorConflictFree(RMQ_CONNECTION_ACTOR_NAME, props);

                rmqPublisherActor = startRmqPublisherActor().orElse(null);

                // create publisher channel
                final ActorRef finalRmqPublisherActor = rmqPublisherActor;
                final CreateChannel createChannel = CreateChannel.apply(
                        ChannelActor.props((channel, channelActor) -> {
                            log.info("Did set up publisher channel: {}. Telling the publisher actor the new channel",
                                    channel);
                            // provide the new channel to the publisher after the channel was connected (also includes reconnects)
                            if (finalRmqPublisherActor != null) {
                                final ChannelCreated channelCreated = new ChannelCreated(channelActor);
                                finalRmqPublisherActor.tell(channelCreated, channelActor);
                            }
                            return null;
                        }),
                        Option.apply(PUBLISHER_CHANNEL));

                PatternsCS.ask(rmqConnectionActor, createChannel, askTimeoutMillis()).handle((reply, throwable) -> {
                    if (throwable != null) {
                        future.complete(new Status.Failure(throwable));
                    } else {
                        future.complete(new Status.Success("channel created"));
                    }
                    return null;
                });
            }
        } else {
            log.debug("Connection <{}> is already open.", connectionId());
            future.complete(new Status.Success("already connected"));
        }
        return future;
    }

    private static void createConsumerChannelAndNotifySelf(final Status.Status status,
            final boolean consuming,
            @Nullable final ActorRef rmqConnectionActor,
            final ActorRef self) {
        if (consuming && status instanceof Status.Success && rmqConnectionActor != null) {
            // send self the created channel
            final CreateChannel createChannel =
                    CreateChannel.apply(ChannelActor.props(SendChannel.to(self)::apply),
                            Option.apply(CONSUMER_CHANNEL));
            // connection actor sends ChannelCreated; use an ASK to swallow the reply in which we are disinterested
            PatternsCS.ask(rmqConnectionActor, createChannel, askTimeoutMillis());
        } else {
            final Object selfMessage = messageFromConnectionStatus(status);
            self.tell(selfMessage, self);
        }
    }

    private Optional<ActorRef> startRmqPublisherActor() {
        if (isPublishing()) {
            return Optional.of(getContext().findChild(RabbitMQPublisherActor.ACTOR_NAME).orElseGet(() -> {
                final Props publisherProps = RabbitMQPublisherActor.props(getTargetsOrEmptySet());
                return startChildActorConflictFree(RabbitMQPublisherActor.ACTOR_NAME, publisherProps);
            }));
        } else {
            return Optional.empty();
        }
    }

    private void stopCommandPublisher() {
        stopChildActor(RabbitMQPublisherActor.ACTOR_NAME);
    }

    private void stopCommandConsumers() {
        consumedTagsToAddresses.clear();
        consumerByAddressWithIndex.forEach((addressWithIndex, child) -> stopChildActor(child));
        consumerByAddressWithIndex.clear();
    }

    private void startCommandConsumers(final Channel channel) {
        log.info("Starting to consume queues...");
        ensureQueuesExist(channel);
        stopCommandConsumers();
        startConsumers(channel);
    }

    private void startConsumers(final Channel channel) {
        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessorActor();
        if (messageMappingProcessor.isPresent()) {
            getSourcesOrEmptySet().forEach(source ->
                    source.getAddresses().forEach(sourceAddress -> {
                        for (int i = 0; i < source.getConsumerCount(); i++) {
                            final String addressWithIndex = sourceAddress + "-" + i;
                            final AuthorizationContext authorizationContext = source.getAuthorizationContext();
                            final ActorRef consumer = startChildActorConflictFree(
                                    CONSUMER_ACTOR_PREFIX + addressWithIndex,
                                    RabbitMQConsumerActor.props(sourceAddress, messageMappingProcessor.get(),
                                            authorizationContext));
                            consumerByAddressWithIndex.put(addressWithIndex, consumer);
                            try {
                                final String consumerTag = channel.basicConsume(sourceAddress, false,
                                        new RabbitMQMessageConsumer(consumer, channel));
                                log.debug("Consuming queue <{}>, consumer tag is <{}>.", addressWithIndex, consumerTag);
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
        final Collection<String> missingQueues = new ArrayList<>();
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

    private static long askTimeoutMillis() {
        // 45% of connection timeout
        return CONNECTING_TIMEOUT * 450L;
    }

    /**
     * Custom exception handler which handles exception during connection.
     */
    private static final class RabbitMQExceptionHandler extends DefaultExceptionHandler {

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

    private static final class RmqConsumerChannelCreated implements ClientConnected {

        private final Channel channel;

        private RmqConsumerChannelCreated(final Channel channel) {
            this.channel = channel;
        }

        private Channel getChannel() {
            return channel;
        }

        @Override
        public Optional<ActorRef> getOrigin() {
            return Optional.empty();
        }

    }

    private static final class SendChannel implements FI.Apply2<Channel, ActorRef, Object> {

        private final ActorRef recipient;

        private SendChannel(final ActorRef recipient) {
            this.recipient = recipient;
        }

        private static SendChannel to(final ActorRef recipient) {
            return new SendChannel(recipient);
        }

        @Override
        public Object apply(final Channel channel, final ActorRef channelActor) {
            recipient.tell(new RmqConsumerChannelCreated(channel), channelActor);
            return channel;
        }

    }

    /**
     * Custom consumer which is notified about different events related to the connection in order to track
     * connectivity status.
     */
    private final class RabbitMQMessageConsumer extends DefaultConsumer {

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

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                log.info("Consume OK for consumer queue <{}> on connection <{}>.", consumingQueueByTag, connectionId());
            }

            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN,
                    "Consumer started at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleCancel(final String consumerTag) throws IOException {
            super.handleCancel(consumerTag);
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                log.warning("Consumer with queue <{}> was cancelled on connection <{}>. This can happen for " +
                        "example when the queue was deleted.", consumingQueueByTag, connectionId());
            }

            consumerActor.tell(ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                    "Consumer for queue cancelled at " + Instant.now(), 0, null), null);
        }

        @Override
        public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
            super.handleShutdownSignal(consumerTag, sig);
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                log.warning("Consumer with queue <{}> shutdown as the channel or the underlying connection has " +
                        "been shut down on connection <{}>.", consumingQueueByTag, connectionId());
            }

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
