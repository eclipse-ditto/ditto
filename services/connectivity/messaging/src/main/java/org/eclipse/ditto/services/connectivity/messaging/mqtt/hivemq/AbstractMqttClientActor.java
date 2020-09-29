/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.AbstractMqttSubscriptionHandler.MqttConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.BaseClientState;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;

import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;

/**
 * Actor which handles connection to an MQTT 3 or 5 broker.
 *
 * @param <S> type of Subscribe messages.
 * @param <P> type of Publish messages.
 * @param <Q> type of mQtt client.
 * @param <R> type of subscription Replies.
 */
abstract class AbstractMqttClientActor<S, P, Q, R> extends BaseClientActor {

    // status for consumer creation (always successful)
    private static final Status.Success CONSUMERS_CREATED = new Status.Success("consumers created");

    private final Connection connection;
    private final HiveMqttClientFactory<Q, ?> clientFactory;
    private final MqttSpecificConfig mqttSpecificConfig;

    @Nullable private Q publisherClient;
    @Nullable private Q client;
    @Nullable private AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler;
    @Nullable private ActorRef publisherActor;

    AbstractMqttClientActor(final Connection connection,
            @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor,
            final HiveMqttClientFactory<Q, ?> clientFactory) {

        super(connection, proxyActor, connectionActor);
        this.connection = connection;
        this.clientFactory = clientFactory;
        mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection);
    }

    /**
     * Create a subscription handler.
     *
     * @param connection the connection.
     * @param client the client.
     * @param logger the logger of the client actor.
     * @return the subscription handler.
     */
    abstract AbstractMqttSubscriptionHandler<S, P, R> createSubscriptionHandler(Connection connection, Q client,
            ThreadSafeDittoLoggingAdapter logger);

    /**
     * Send a CONN message.
     *
     * @param client the client to send the message with.
     * @param cleanSession whether to set the clean-session/clean-start flag.
     * @return the CONNACK future.
     */
    abstract CompletionStage<?> sendConn(Q client, boolean cleanSession);

    /**
     * Disconnect the client.
     *
     * @param client the client.
     * @return future that completes when disconnect succeeds.
     */
    abstract CompletionStage<Void> disconnectClient(Q client);

    /**
     * Start a publisher actor for this client actor.
     *
     * @param connection the connection.
     * @param client the client.
     * @return reference of the created publisher actor.
     */
    abstract ActorRef startPublisherActor(Connection connection, Q client);

    /**
     * Start a consumer actor.
     *
     * @param dryRun whether this is a dry-run (consumed messages are discarded)
     * @param source source of the consumer actor.
     * @param mappingActor the message mapping processor actor.
     * @param specificConfig the MQTT specific config.
     * @return reference of the created publisher actor.
     */
    abstract ActorRef startConsumerActor(boolean dryRun, Source source, ActorRef mappingActor,
            MqttSpecificConfig specificConfig);

    @Override
    protected void doInit() {
        createClientAndSubscriptionHandler();
    }

    @Override
    public void postStop() {
        safelyDisconnectClient(client);
        if (publisherClient != client) {
            safelyDisconnectClient(publisherClient);
        }
        super.postStop();
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        if (mqttSpecificConfig.reconnectForRedelivery()) {
            return super.inConnectedState()
                    .eventEquals(Control.RECONNECT_CONSUMER_CLIENT, this::reconnectConsumerClient)
                    .eventEquals(Control.DO_RECONNECT_CONSUMER_CLIENT, this::doReconnectConsumerClient);
        } else {
            return super.inConnectedState();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> reconnectConsumerClient(final Control reconnectConsumerClient,
            final BaseClientData data) {

        // Restart once in 10 seconds max
        final Control trigger = Control.DO_RECONNECT_CONSUMER_CLIENT;
        if (!isTimerActive(trigger.name())) {
            if (mqttSpecificConfig.separatePublisherClient()) {
                final Duration delay = getReconnectForRedeliveryDelayWithLowerBound();
                logger.info("Restarting consumer client in <{}> by request.", delay);
                setTimer(trigger.name(), trigger, delay);
            } else {
                // fail the connection to reconnect
                final ConnectionFailure failure =
                        new ImmutableConnectionFailure(null, null,
                                "Restarting connection due to unfulfilled acknowledgement requests.");
                getSelf().tell(failure, ActorRef.noSender());
            }
        }
        return stay();
    }

    private Duration getReconnectForRedeliveryDelayWithLowerBound() {
        final Duration configuredDelay = mqttSpecificConfig.getReconnectForDeliveryDelay();
        final Duration lowerBound = Duration.ofSeconds(1L);
        return lowerBound.minus(configuredDelay).isNegative() ? configuredDelay : lowerBound;
    }

    private FSM.State<BaseClientState, BaseClientData> doReconnectConsumerClient(final Control reconnectConsumerClient,
            final BaseClientData data) {

        final Q oldClient = getClient();
        final AbstractMqttSubscriptionHandler<S, P, R> oldSubscriptionHandler = getSubscriptionHandler();
        safelyDisconnectClient(oldClient);
        createSubscriberClientAndSubscriptionHandler();
        oldSubscriptionHandler.stream().forEach(getSubscriptionHandler()::handleMqttConsumer);
        subscribeAndSendConn(false).whenComplete(
                (result, error) -> logger.info("Consumer client restarted: result{}, error={]", result, error));

        return stay();
    }

    /**
     * Create the client ID for this client actor. Default to connection ID.
     * For connections with more than 1 client count, the client ID
     * Override this method to customize client IDs further.
     *
     * @param connection the connection.
     * @param mqttSpecificConfig the specific config.
     * @return the client ID in the config, or use the connection ID as client ID if not configured, with instance ID
     * appended when client count is not 1.
     */
    protected String resolveMqttClientId(final Connection connection, final MqttSpecificConfig mqttSpecificConfig) {
        final String singleClientId = mqttSpecificConfig.getMqttClientId().orElse(connection.getId().toString());
        return appendInstanceIdForNonEmptyClientId(singleClientId, connection);
    }

    /**
     * Create the client ID for the publisher client if configured to use a separate publisher client.
     *
     * @param connection the connection.
     * @param config the specific config
     * @return the client ID in
     */
    protected String resolvePublisherClientId(final Connection connection, final MqttSpecificConfig config) {
        // default to configured client ID + 'p'
        //  fall back to connection ID + 'p'
        //  do not default to empty client ID - support for empty client ID is not guaranteed by the spec.
        final String publisherId = config.getMqttPublisherId()
                .or(() -> config.getMqttClientId().map(cId -> cId + "p"))
                .orElseGet(() -> connection.getId().toString() + "p");
        return appendInstanceIdForNonEmptyClientId(publisherId, connection);
    }

    /**
     * Create a new client using the configuration of this actor.
     * On failure, send a ConnectionFailure to self.
     */
    private void createClientAndSubscriptionHandler() {
        final ActorRef self = getContext().getSelf();
        try {
            createSubscriberClientAndSubscriptionHandler();
            if (mqttSpecificConfig.separatePublisherClient()) {
                final String publisherClientId = resolvePublisherClientId(connection, mqttSpecificConfig);
                publisherClient = clientFactory.newClient(connection, publisherClientId, true);
            } else {
                // use the same client for subscribers and publisher
                publisherClient = client;
            }
        } catch (final Exception e) {
            logger.debug("Connecting failed ({}): {}", e.getClass().getName(), e.getMessage());
            resetClientAndSubscriptionHandler();
            self.tell(new ImmutableConnectionFailure(self, e, null), self);
        }
    }

    private void createSubscriberClientAndSubscriptionHandler() {
        final String mqttClientId = resolveMqttClientId(connection, mqttSpecificConfig);
        client = clientFactory.newClient(connection, mqttClientId, true);
        subscriptionHandler = createSubscriptionHandler(connection, client, logger);
    }

    private void resetClientAndSubscriptionHandler() {
        publisherClient = null;
        client = null;
        subscriptionHandler = null;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        final Connection connectionToBeTested = testConnectionCommand.getConnection();
        final MqttSpecificConfig mqttSpecificConfig = MqttSpecificConfig.fromConnection(connectionToBeTested);
        final String mqttClientId = resolveMqttClientId(connectionToBeTested, mqttSpecificConfig);
        // attention: do not use reconnect, otherwise the future never returns
        final Q testClient;
        try {
            testClient = clientFactory.newClient(connectionToBeTested, mqttClientId, false);
        } catch (final Exception e) {
            return CompletableFuture.completedFuture(new Status.Failure(e.getCause()));
        }
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(testConnectionCommand)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionToBeTested.getId());
        final AbstractMqttSubscriptionHandler<S, P, R> testSubscriptions =
                createSubscriptionHandler(connectionToBeTested, testClient, l);
        // always use clean session for tests to not have broker persist anything
        final boolean cleanSession = true;
        return sendConn(testClient, cleanSession)
                .thenApply(connAck -> {
                    final String url = connectionToBeTested.getUri();
                    final String message = "MQTT connection to " + url + " established successfully.";
                    l.info("{} {}", message, connAck);
                    return (Status.Status) new Status.Success(message);
                })
                .thenCompose(status -> {
                    startPublisherActor(connection(), testClient);
                    return CompletableFuture.completedFuture(new Status.Success("publisher started"));
                })
                .thenCompose(s -> {
                    startHiveMqConsumers(testSubscriptions::handleMqttConsumer);
                    return testSubscriptions.subscribe();
                })
                .handle((s, t) -> {
                    final Status.Status status;
                    if (t == null) {
                        final String message = "Connection test for was successful.";
                        connectionLogger.success(message);
                        l.info("Connection test for {} was successful.", connectionId());
                        status = new Status.Success(message);
                    } else {
                        l.info("Connection test to {} failed: {}", connectionToBeTested.getUri(), t.getMessage());
                        connectionLogger.failure("Connection test failed: {0}", t.getMessage());
                        status = new Status.Failure(t);
                    }
                    stopCommandConsumers(testSubscriptions);
                    stopChildActor(publisherActor);
                    safelyDisconnectClient(testClient);
                    return status;
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // start publisher and consumer actors first.
        // after that, subscribe and connect in parallel in order to receive redelivered PUBLISH messages.
        if (client == null) {
            createClientAndSubscriptionHandler();
            if (client == null) {
                // client creation failed; a ConnectionFailure event will arrive and cause transition to failure state
                return;
            }
        }
        final CompletionStage<Object> startActorsResult = startPublisherAndConsumerActors(null)
                .thenApply(result -> result.isSuccess() ? new MqttClientConnected(origin) : result.getFailure());
        Patterns.pipe(startActorsResult, getContext().dispatcher()).to(getSelf());
    }

    @Override
    protected State<BaseClientState, BaseClientData> clientConnectedInConnectingState(
            final ClientConnected clientConnected,
            final BaseClientData data) {

        Patterns.pipe(subscribeAndSendConn(true), getContext().getDispatcher())
                .to(getSelf(), clientConnected.getOrigin().orElseGet(ActorRef::noSender));

        return stay();
    }

    private CompletionStage<InitializationResult> subscribeAndSendConn(final boolean connectPublisher) {
        // add subscriber streams right away, but this future won't complete until SUBACK arrive after CONNACK
        final CompletionStage<List<R>> subAckFuture = getSubscriptionHandler().subscribe();

        // delay CONN by 1s to ensure that subscriber streams are ready before redelivered PUBLISH messages arrive
        final CompletableFuture<InitializationResult> connAckFuture =
                sendConnAndExpectConnAck(Duration.ofSeconds(1L), connectPublisher);

        return CompletableFuture.allOf(connAckFuture, subAckFuture.toCompletableFuture())
                .thenApply(_void -> connAckFuture.join())
                .exceptionally(InitializationResult::failed);
    }

    private CompletableFuture<InitializationResult> sendConnAndExpectConnAck(final Duration delay,
            final boolean connectPublisher) {
        final Q client = getClient();
        final CompletableFuture<Void> delayFuture = new CompletableFuture<>();
        delayFuture.completeOnTimeout(null, delay.toMillis(), TimeUnit.MILLISECONDS);
        final CompletableFuture<?> publisherConnFuture = connectPublisherClient(connectPublisher);
        // if there is no reconnect-redelivery, do not use a persistent session, since redelivered messages
        // will only arrive after
        final boolean cleanSession = mqttSpecificConfig.cleanSession();
        return CompletableFuture.allOf(delayFuture, publisherConnFuture)
                .thenCompose(_void -> sendConn(client, cleanSession).handle(this::handleConnAck));
    }

    private InitializationResult handleConnAck(@Nullable final Object connAck, @Nullable final Throwable throwable) {
        if (throwable == null) {
            logger.debug("CONNACK {}", connAck);
            return InitializationResult.success();
        } else {
            logger.debug("CONN failed: {}", throwable);
            return InitializationResult.failed(throwable);
        }
    }

    private CompletableFuture<?> connectPublisherClient(final boolean connectPublisher) {
        if (connectPublisher && publisherClient != null && client != publisherClient) {
            // if publisher client is separate, start with clean session.
            return sendConn(publisherClient, false).toCompletableFuture();
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        startHiveMqConsumers(getSubscriptionHandler()::handleMqttConsumer);
        return CompletableFuture.completedFuture(CONSUMERS_CREATED);
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        publisherActor = startPublisherActor(connection(), checkNotNull(publisherClient, "publisherClient"));
        return CompletableFuture.completedFuture(DONE);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        if (client != null) {
            final CompletionStage<ClientDisconnected> disconnectFuture = disconnectClient(getClient())
                    .handle((aVoid, throwable) -> {
                        if (null != throwable) {
                            logger.info("Error while disconnecting: {}", throwable);
                        } else {
                            logger.debug("Successfully disconnected.");
                        }
                        return getClientDisconnected(origin);
                    });
            Patterns.pipe(disconnectFuture, getContext().getDispatcher()).to(getSelf(), origin);
        } else {
            // client is already disconnected
            getSelf().tell(getClientDisconnected(origin), origin);
        }
    }

    private static ClientDisconnected getClientDisconnected(@Nullable final ActorRef origin) {
        return () -> Optional.ofNullable(origin);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        stopCommandConsumers(subscriptionHandler);
        stopChildActor(publisherActor);
        safelyDisconnectClient(client);
        if (publisherClient != client) {
            safelyDisconnectClient(publisherClient);
        }
        resetClientAndSubscriptionHandler();
    }

    @Override
    @Nullable
    protected ActorRef getPublisherActor() {
        return publisherActor;
    }

    /**
     * Call only in case of a failure to make sure the client is closed properly. E.g. when subscribing to a topic
     * failed the connection is already established and must be closed to avoid resource leaks.
     *
     * @param client the client to disconnect
     */
    private CompletionStage<Void> safelyDisconnectClient(@Nullable final Q client) {
        if (client != null) {
            try {
                logger.debug("Disconnecting mqtt client, ignoring any errors.");
                return disconnectClient(client);
            } catch (final Exception e) {
                logger.debug("Disconnecting client failed, it was probably already closed: {}", e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void startHiveMqConsumers(final Consumer<MqttConsumer> consumerListener) {
        connection().getSources().stream()
                .map(source -> MqttConsumer.of(source,
                        startConsumerActor(isDryRun(), source, getMessageMappingProcessorActor(), mqttSpecificConfig)))
                .forEach(consumerListener);
    }

    private void stopCommandConsumers(@Nullable final AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler) {
        if (subscriptionHandler != null) {
            subscriptionHandler.clearConsumerActors(this::stopChildActor);
        }
    }

    private Q getClient() {
        if (null == client) {
            throw new IllegalStateException("MqttClient not initialized!");
        }
        return client;
    }

    private AbstractMqttSubscriptionHandler<S, P, R> getSubscriptionHandler() {
        if (null == subscriptionHandler) {
            throw new IllegalStateException("MqttSubscriptionHandler not initialized!");
        }
        return subscriptionHandler;
    }

    private static String appendInstanceIdForNonEmptyClientId(final String clientId, final Connection connection) {
        if (connection.getClientCount() == 1 || clientId.isEmpty()) {
            return clientId;
        } else {
            return clientId + "_" + InstanceIdentifierSupplier.getInstance().get();
        }
    }


    static class MqttClientConnected extends AbstractWithOrigin implements ClientConnected {

        MqttClientConnected(@Nullable final ActorRef origin) {
            super(origin);
        }

        @Override
        public Optional<ActorRef> getOrigin() {
            return Optional.empty();
        }
    }

    enum Control {
        RECONNECT_CONSUMER_CLIENT,
        DO_RECONNECT_CONSUMER_CLIENT
    }
}
