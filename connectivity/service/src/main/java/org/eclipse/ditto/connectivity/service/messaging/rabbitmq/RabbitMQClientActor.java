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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.ClientConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientData;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;

import com.newmotion.akka.rabbitmq.ChannelActor;
import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.CreateChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FI;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
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
    private final Map<String, String> consumedTagsToAddresses;
    private final Map<String, ActorRef> consumerByAddressWithIndex;

    @Nullable private ActorRef rmqConnectionActor;
    private ActorRef rmqPublisherActor;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private RabbitMQClientActor(final Connection connection,
            final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityOverwritesConfig) {

        super(connection, commandForwarderActor, connectionActor, dittoHeaders, connectivityOverwritesConfig);

        rabbitConnectionFactoryFactory =
                ConnectionBasedRabbitConnectionFactoryFactory.getInstance(this::getSshTunnelState);
        consumedTagsToAddresses = new HashMap<>();
        consumerByAddressWithIndex = new HashMap<>();

        rmqConnectionActor = null;
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    @SuppressWarnings("unused")
    private RabbitMQClientActor(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor,
            final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory,
            final DittoHeaders dittoHeaders,
            final Config connectivityOverwritesConfig) {

        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityOverwritesConfig);
        this.rabbitConnectionFactoryFactory = rabbitConnectionFactoryFactory;
        consumedTagsToAddresses = new HashMap<>();
        consumerByAddressWithIndex = new HashMap<>();

        rmqConnectionActor = null;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param commandForwarderActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @param connectivityOverwritesConfig the overwrites for the connectivity config for the given connection.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityOverwritesConfig) {

        return Props.create(RabbitMQClientActor.class, validateConnection(connection), commandForwarderActor,
                connectionActor, dittoHeaders, connectivityOverwritesConfig);
    }

    @Override
    protected Set<Pattern> getExcludedAddressReportingChildNamePatterns() {
        final Set<Pattern> excludedChildNamePatterns =
                new HashSet<>(super.getExcludedAddressReportingChildNamePatterns());
        excludedChildNamePatterns.add(Pattern.compile(RMQ_CONNECTION_ACTOR_NAME));
        return excludedChildNamePatterns;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param rabbitConnectionFactoryFactory the ConnectionFactory Factory to use.
     * @return the Akka configuration Props object.
     */
    static Props propsForTests(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor, final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory) {

        return Props.create(RabbitMQClientActor.class, validateConnection(connection), proxyActor, connectionActor,
                rabbitConnectionFactoryFactory, DittoHeaders.empty(), ConfigFactory.empty());
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
        return super.inConnectedState().event(ClientConnected.class, BaseClientData.class, (event, data) -> {
            // when connection is lost, the library (ChannelActor) will automatically reconnect
            // without the state of this actor changing. But we will receive a new ClientConnected message
            // that we can use to bind our consumers to the channels.
            startConsumerActors(event);
            return stay();
        });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        final ClientConfig clientConfig = connectivityConfig().getClientConfig();

        // should be smaller than the global testing timeout to be able to send a response
        final Duration createChannelTimeout = clientConfig.getTestingTimeout().dividedBy(10L).multipliedBy(8L);
        final Duration internalReconnectTimeout = clientConfig.getTestingTimeout();

        // does explicitly not test the consumer so we won't consume any messages by accident.
        final DittoHeaders dittoHeaders = testConnectionCommand.getDittoHeaders();
        final String correlationId = dittoHeaders.getCorrelationId().orElse(null);
        return connect(testConnectionCommand.getConnection(), correlationId, createChannelTimeout,
                internalReconnectTimeout);
    }

    @Override
    protected CompletionStage<Void> stopConsuming() {
        final var timeout = Duration.ofMinutes(2L);
        final CompletableFuture<?>[] futures = consumerByAddressWithIndex.values()
                .stream()
                .map(child -> Patterns.ask(child, RabbitMQConsumerActor.Control.STOP_CONSUMER, timeout))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final boolean consuming = isConsuming();
        final ActorRef self = getSelf();
        final ClientConfig clientConfig = connectivityConfig().getClientConfig();

        // #connect() will only create the channel for the the producer, but not the consumer. We need to split the
        // connecting timeout to work for both channels before the global connecting timeout happens.
        // We choose about 45% of the global connecting timeout for this
        final Duration splitDuration = clientConfig.getConnectingMinTimeout().dividedBy(100L).multipliedBy(45L);
        final Duration internalReconnectTimeout = clientConfig.getConnectingMinTimeout();
        final CompletionStage<Status.Status> connect =
                connect(connection, null, splitDuration, internalReconnectTimeout);
        connect.thenAccept(status -> createConsumerChannelAndNotifySelf(status, consuming, self, splitDuration));
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        getSelf().tell(ClientDisconnected.of(origin, shutdownAfterDisconnect), origin);
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        if (clientConnected instanceof RmqConsumerChannelCreated rmqConsumerChannelCreated) {
            try {
                startCommandConsumers(rmqConsumerChannelCreated.getChannel());
            } catch (final Exception error) {
                return CompletableFuture.failedStage(error);
            }
        }
        return super.startConsumerActors(clientConnected);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        logger.debug("cleaning up");
        stopCommandConsumers();
        stopChildActor(rmqPublisherActor);
        stopChildActor(rmqConnectionActor);
        rmqConnectionActor = null;
    }

    @Override
    protected ActorRef getPublisherActor() {
        return rmqPublisherActor;
    }

    private Optional<ConnectionFactory> tryToCreateConnectionFactory(
            final RabbitConnectionFactoryFactory factoryFactory,
            final Connection connection,
            final RabbitMQExceptionHandler rabbitMQExceptionHandler) {

        try {
            return Optional.of(
                    factoryFactory.createConnectionFactory(connection, rabbitMQExceptionHandler, connectionLogger)
            );
        } catch (final Throwable throwable) {
            // error creating factory; return early.)
            rabbitMQExceptionHandler.exceptionHandler.accept(throwable);
            return Optional.empty();
        }
    }

    private static Object messageFromConnectionStatus(final Status.Status status) {
        if (status instanceof Status.Failure failure) {
            return ConnectionFailure.of(null, failure.cause(), null);
        } else {
            return (ClientConnected) Optional::empty;
        }
    }

    private CompletionStage<Status.Status> connect(final Connection connection,
            @Nullable final CharSequence correlationId,
            final Duration createChannelTimeout,
            final Duration internalReconnectTimeout) {

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(correlationId)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
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
                        FiniteDuration.apply(internalReconnectTimeout.getSeconds(), TimeUnit.SECONDS),
                        (rmqConnection, connectionActorRef) -> {
                            l.info("Established RMQ connection: {}", rmqConnection);
                            return null;
                        });

                rmqConnectionActor = startChildActorConflictFree(RMQ_CONNECTION_ACTOR_NAME, props);
                final ActorRef currentPublisherActor = startRmqPublisherActor();
                rmqPublisherActor = currentPublisherActor;

                // create publisher channel
                final CreateChannel createChannel = CreateChannel.apply(
                        ChannelActor.props((channel, channelActor) -> {
                            l.info("Did set up publisher channel: {}. Telling the publisher actor the new channel",
                                    channel);
                            // provide the new channel to the publisher after the channel was connected (also includes reconnects)
                            final ChannelCreated channelCreated = new ChannelCreated(channelActor);
                            currentPublisherActor.tell(channelCreated, channelActor);
                            return null;
                        }),
                        Option.apply(PUBLISHER_CHANNEL));

                Patterns.ask(rmqConnectionActor, createChannel, createChannelTimeout).handle((reply, throwable) -> {
                    if (throwable != null) {
                        future.complete(new Status.Failure(throwable));
                    } else {
                        // waiting for "final RabbitMQExceptionHandler rabbitMQExceptionHandler" to get its chance to
                        // complete the future with an Exception before we report Status.Success right now
                        // so delay this by 1 second --
                        future.completeOnTimeout(new Status.Success("channel created"), 1, TimeUnit.SECONDS);
                    }
                    return null;
                });
            }
        } else {
            l.debug("Connection <{}> is already open.", connection.getId());
            future.complete(new Status.Success("already connected"));
        }
        return future;

    }

    private void createConsumerChannelAndNotifySelf(final Status.Status status, final boolean consuming,
            final ActorRef self, final Duration createChannelTimeout) {

        if (consuming && status instanceof Status.Success && null != rmqConnectionActor) {
            // send self the created channel
            final CreateChannel createChannel =
                    CreateChannel.apply(ChannelActor.props(SendChannel.to(self)::apply),
                            Option.apply(CONSUMER_CHANNEL));
            // connection actor sends ChannelCreated; use an ASK to swallow the reply in which we are disinterested
            Patterns.ask(rmqConnectionActor, createChannel, createChannelTimeout);
        } else {
            final Object selfMessage = messageFromConnectionStatus(status);
            self.tell(selfMessage, self);
        }
    }

    private ActorRef startRmqPublisherActor() {
        stopChildActor(rmqPublisherActor);
        final Props publisherProps = RabbitMQPublisherActor.props(connection(),
                connectivityStatusResolver,
                connectivityConfig());
        return startChildActorConflictFree(RabbitMQPublisherActor.ACTOR_NAME, publisherProps);
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        return CompletableFuture.completedFuture(DONE);
    }

    private void stopCommandConsumers() {
        consumedTagsToAddresses.clear();
        consumerByAddressWithIndex.forEach((addressWithIndex, child) -> stopChildActor(child));
        consumerByAddressWithIndex.clear();
    }

    private void startCommandConsumers(final Channel channel) {
        logger.info("Starting to consume queues...");
        ensureQueuesExist(channel);
        stopCommandConsumers();
        startConsumers(channel);
    }

    private void startConsumers(final Channel channel) {
        getSourcesOrEmptyList().forEach(source ->
                source.getAddresses().forEach(sourceAddress -> {
                    for (int i = 0; i < source.getConsumerCount(); i++) {
                        final String addressWithIndex = sourceAddress + "-" + i;
                        final ActorRef consumer = startChildActorConflictFree(
                                CONSUMER_ACTOR_PREFIX + addressWithIndex,
                                RabbitMQConsumerActor.props(sourceAddress, getInboundMappingSink(), source,
                                        channel, connection(), connectivityStatusResolver, connectivityConfig()));
                        consumerByAddressWithIndex.put(addressWithIndex, consumer);
                        try {
                            final String consumerTag = channel.basicConsume(sourceAddress, false,
                                    new RabbitMQMessageConsumer(consumer, channel, sourceAddress));
                            logger.debug("Consuming queue <{}>, consumer tag is <{}>.", addressWithIndex, consumerTag);
                            consumedTagsToAddresses.put(consumerTag, addressWithIndex);
                        } catch (final IOException e) {
                            connectionLogger.failure("Failed to consume queue {0}: {1}", addressWithIndex,
                                    e.getMessage());
                            logger.warning("Failed to consume queue <{}>: <{}>", addressWithIndex, e.getMessage());
                        }
                    }
                })
        );
    }

    private void ensureQueuesExist(final Channel channel) {
        final Collection<String> missingQueues = new ArrayList<>();
        getSourcesOrEmptyList().forEach(consumer ->
                consumer.getAddresses().forEach(address -> {
                    try {
                        channel.queueDeclarePassive(address);
                    } catch (final IOException e) {
                        missingQueues.add(address);
                        logger.warning("The queue <{}> does not exist.", address);
                    } catch (final AlreadyClosedException e) {
                        if (!missingQueues.isEmpty()) {
                            // Our client will automatically close the connection if a queue does not exists. This will
                            // cause an AlreadyClosedException for the following queue (e.g. ['existing1', 'notExisting', -->'existing2'])
                            // That's why we will ignore this error if the missingQueues list isn't empty.
                            logger.warning(
                                    "Received exception of type {} when trying to declare queue {}. This happens when a previous " +
                                            "queue was missing and thus the connection got closed.",
                                    e.getClass().getName(), address);
                        } else {
                            logger.error(e, "Exception while declaring queue {}", address);
                            throw e;
                        }
                    }
                })
        );
        if (!missingQueues.isEmpty()) {
            logger.warning("Stopping RMQ client actor for connection <{}> as queues to connect to are missing: <{}>",
                    connectionId(), missingQueues);
            connectionLogger.failure("Can not connect to RabbitMQ as queues are missing: {0}", missingQueues);
            throw ConnectionFailedException.newBuilder(connectionId())
                    .description("The queues " + missingQueues + " to connect to are missing.")
                    .build();
        }
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
     * Custom consumer which is notified about different events related to the connection in order to track connectivity
     * status.
     */
    private final class RabbitMQMessageConsumer extends DefaultConsumer {

        private final ActorRef consumerActor;
        private final String address;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param consumerActor the ActorRef to the consumer actor
         * @param channel the channel to which this consumer is attached
         * @param address the address of the consumer
         */
        private RabbitMQMessageConsumer(final ActorRef consumerActor,
                final Channel channel, final String address) {
            super(channel);
            this.consumerActor = consumerActor;
            this.address = address;
            updateSourceStatus(ConnectivityStatus.OPEN, "Consumer initialized at " + Instant.now());
        }

        @Override
        public void handleDelivery(final String consumerTag, final Envelope envelope,
                final AMQP.BasicProperties properties, final byte[] body) {

            consumerActor.tell(new Delivery(envelope, properties, body), getSelf());
        }

        @Override
        public void handleConsumeOk(final String consumerTag) {
            super.handleConsumeOk(consumerTag);

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                connectionLogger.success("Consume OK for consumer queue {0}", consumingQueueByTag);
                logger.info("Consume OK for consumer queue <{}> on connection <{}>", consumingQueueByTag,
                        connectionId());
            }

            updateSourceStatus(ConnectivityStatus.OPEN, "Consumer started at " + Instant.now());
        }

        @Override
        public void handleCancel(final String consumerTag) throws IOException {
            super.handleCancel(consumerTag);

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                connectionLogger.failure("Consumer with queue {0} was cancelled. This can happen for example " +
                        "when the queue was deleted", consumingQueueByTag);
                logger.info("Consumer with queue <{}> was cancelled on connection <{}>. This can happen for " +
                        "example when the queue was deleted", consumingQueueByTag, connectionId());
            }

            updateSourceStatus(ConnectivityStatus.MISCONFIGURED, "Consumer for queue cancelled at " + Instant.now());
        }

        @Override
        public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
            super.handleShutdownSignal(consumerTag, sig);

            final String consumingQueueByTag = consumedTagsToAddresses.get(consumerTag);
            if (null != consumingQueueByTag) {
                connectionLogger.failure(
                        "Consumer with queue <{0}> shutdown as the channel or the underlying connection has " +
                                "been shut down", consumingQueueByTag);
                logger.warning("Consumer with queue <{}> shutdown as the channel or the underlying connection has " +
                        "been shut down on connection <{}>", consumingQueueByTag, connectionId());
            }

            final ConnectivityStatus failureStatus = connectivityStatusResolver.resolve(sig);
            updateSourceStatus(failureStatus,
                    "Channel or the underlying connection has been shut down at " + Instant.now());
        }

        @Override
        public void handleRecoverOk(final String consumerTag) {
            super.handleRecoverOk(consumerTag);

            logger.info("Recovered OK for consumer with tag <{}> on connection <{}>", consumerTag, connectionId());

            getSelf().tell((ClientConnected) Optional::empty, getSelf());
        }

        private void updateSourceStatus(final ConnectivityStatus connectionStatus, final String statusDetails) {
            consumerActor.tell(ConnectivityModelFactory.newStatusUpdate(InstanceIdentifierSupplier.getInstance().get(),
                    connectionStatus, address, statusDetails, Instant.now()), ActorRef.noSender());
        }

    }

}
