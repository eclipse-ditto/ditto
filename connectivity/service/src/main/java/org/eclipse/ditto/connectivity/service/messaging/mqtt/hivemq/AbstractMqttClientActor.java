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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.AbstractMqttSubscriptionHandler.MqttConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientData;
import org.eclipse.ditto.connectivity.service.messaging.backoff.RetryTimeoutStrategy;
import org.eclipse.ditto.connectivity.service.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Sink;

/**
 * Actor which handles connection to an MQTT 3 or 5 broker.
 *
 * @param <S> type of Subscribe messages.
 * @param <P> type of Publish messages.
 * @param <Q> type of mqtt client.
 * @param <R> type of subscription replies.
 */
abstract class AbstractMqttClientActor<S, P, Q extends MqttClient, R> extends BaseClientActor {

    // status for consumer creation (always successful)
    private static final Status.Success CONSUMERS_CREATED = new Status.Success("consumers created");
    private static final String CONSUMER = "consumer";
    private static final String PUBLISHER = "publisher";

    private final Connection connection;
    private final MqttSpecificConfig mqttSpecificConfig;

    @Nullable private AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler;

    @Nullable private ClientWithCancelSwitch client;
    @Nullable private ClientWithCancelSwitch publisherClient;

    @Nullable private ActorRef publisherActor;
    private final MqttConfig mqttConfig;

    AbstractMqttClientActor(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        this.connection = connection;
        mqttConfig = connectivityConfig().getConnectionConfig().getMqttConfig();
        mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);
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
     * @param keepAliveInterval the time interval in which the client sends a ping to the broker (client default
     * is used for {@code null} value, {@code 0} value disables sending keep alive pings)
     * @return the CONNACK future.
     */
    abstract CompletionStage<?> sendConn(Q client, boolean cleanSession, @Nullable final Duration keepAliveInterval);

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
     * @param inboundMappingSink the inbound message mapping sink.
     * @param specificConfig the MQTT specific config.
     * @return reference of the created publisher actor.
     */
    abstract ActorRef startConsumerActor(boolean dryRun, Source source, Sink<Object, NotUsed> inboundMappingSink,
            MqttSpecificConfig specificConfig);

    /**
     * @return the factory that creates new HiveMqttClients
     */
    abstract HiveMqttClientFactory<Q, ?> getClientFactory();

    @Override
    public void postStop() {
        logger.info("actor stopped, stopping clients");
        safelyDisconnectClient(client, CONSUMER, true);
        safelyDisconnectClient(publisherClient, PUBLISHER, true);
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

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        if (mqttSpecificConfig.reconnectForRedelivery()) {
            return super.inConnectingState()
                    .eventEquals(Control.RECONNECT_CONSUMER_CLIENT, this::reconnectConsumerClient)
                    .eventEquals(Control.DO_RECONNECT_CONSUMER_CLIENT, this::doReconnectConsumerClient);
        } else {
            return super.inConnectingState();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> reconnectConsumerClient(final Control reconnectConsumerClient,
            final BaseClientData data) {

        // Restart once in "getReconnectForRedeliveryDelayWithLowerBound()" duration max
        final Control trigger = Control.DO_RECONNECT_CONSUMER_CLIENT;
        if (!isTimerActive(trigger.name())) {
            final Duration delay = getReconnectForRedeliveryDelayWithLowerBound();
            logger.info("Restarting consumer client in <{}> by request.", delay);
            startSingleTimer(trigger.name(), trigger, delay);
        } else {
            logger.debug("Timer <{}> is already active, not requesting restarting consumer client again",
                    trigger.name());
        }
        return stay();
    }

    private Duration getReconnectForRedeliveryDelayWithLowerBound() {
        final Duration configuredDelay = mqttSpecificConfig.getReconnectForDeliveryDelay();
        final var lowerBound = Duration.ofSeconds(1L);
        return lowerBound.minus(configuredDelay).isNegative() ? configuredDelay : lowerBound;
    }

    private FSM.State<BaseClientState, BaseClientData> doReconnectConsumerClient(final Control reconnectConsumerClient,
            final BaseClientData data) {

        final ClientWithCancelSwitch oldClient = getClient();
        safelyDisconnectClient(oldClient, CONSUMER, false);
        return stay();
    }

    /**
     * Create the client ID for this client actor. Default to connection ID.
     * For connections with more than 1 client count, the client ID has the format CONFIGURED_CLIENT_ID-UUID,
     * where UUID is unique to each client actor.
     * Override this method to customize client IDs further.
     *
     * @param connection the connection.
     * @param mqttSpecificConfig the specific config.
     * @return the client ID in the config, or use the connection ID as client ID if not configured, with instance ID
     * appended when client count is not 1.
     */
    protected String resolveMqttClientId(final Connection connection, final MqttSpecificConfig mqttSpecificConfig) {
        final String singleClientId = mqttSpecificConfig.getMqttClientId().orElse(connection.getId().toString());
        return distinguishClientIdIfNecessary(singleClientId);
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
                .orElseGet(() -> connection.getId() + "p");
        return distinguishClientIdIfNecessary(publisherId);
    }

    /**
     * Create a new client using the configuration of this actor.
     * On failure, send a ConnectionFailure to self.
     *
     * @param willSubscribe {@code true} whether the created client will subscribe to MQTT topics at all.
     */
    private void createClientAndSubscriptionHandler(final boolean willSubscribe) {
        final ActorRef self = getContext().getSelf();
        try {
            createSubscriberClientAndSubscriptionHandler(willSubscribe);
            if (mqttSpecificConfig.separatePublisherClient()) {
                final String publisherClientId = resolvePublisherClientId(connection, mqttSpecificConfig);
                final var cancelReconnect = new AtomicBoolean(false);
                final Q createdClient = getClientFactory().newClient(connection, publisherClientId, mqttConfig,
                        mqttSpecificConfig,
                        true, // this is the publisher client, always respect last will config
                        getMqttClientConnectedListener(PUBLISHER),
                        getMqttClientDisconnectedListener(PUBLISHER, cancelReconnect),
                        connectionLogger,
                        connectivityConfig().getConnectionConfig().doubleDecodingEnabled());
                publisherClient = new ClientWithCancelSwitch(createdClient, cancelReconnect);
            } else {
                // use the same client for subscribers and publisher
                publisherClient = client;
            }
        } catch (final Exception e) {
            logger.debug("Connecting failed ({}): {}", e.getClass().getName(), e.getMessage());
            resetClientAndSubscriptionHandler();
            self.tell(ConnectionFailure.of(self, e, null), self);
        }
    }

    private MqttClientConnectedListener getMqttClientConnectedListener(final String role) {
        return context -> logger.info("Client with role <{}> (re-)connected: <{}>", role,
                context.getClientConfig().getState());
    }

    private MqttClientDisconnectedListener getMqttClientDisconnectedListener(final String role,
            final AtomicBoolean cancelReconnect) {
        final var retryTimeoutStrategy = RetryTimeoutStrategy.newDuplicationRetryTimeoutStrategy(
                mqttConfig.getReconnectBackOffConfig().getTimeoutConfig());

        return context -> {
            if (context.getReconnector().getAttempts() == 0) {
                retryTimeoutStrategy.reset();
            }

            if (context.getClientConfig().getState() == MqttClientState.CONNECTING) {
                // if the client is in initial CONNECTING state (i.e. was never connected, not reconnecting) we disable
                // the automatic reconnect because the client would continue to connect and the caller would never see
                // the cause why the connection failed
                logger.info("Initial connect failed, disabling automatic reconnect for role <{}>", role);
                context.getReconnector().reconnect(false);
            } else {
                final boolean doReconnect = connection.isFailoverEnabled() && !cancelReconnect.get();
                final long nextTimeout = retryTimeoutStrategy.getNextTimeout().toMillis();
                final long reconnectDelay;
                if (context.getSource() == MqttDisconnectSource.SERVER) {
                    reconnectDelay = Math.max(nextTimeout,
                            mqttConfig.getReconnectMinTimeoutForMqttBrokerInitiatedDisconnect().toMillis());
                } else {
                    reconnectDelay = nextTimeout;
                }
                logger.info("Client with role <{}> disconnected, source: <{}> - reconnecting: <{}> " +
                                "with current retries of <{}> and delay <{}>ms",
                        new Object[]{role, context.getSource(), doReconnect, retryTimeoutStrategy.getCurrentTries(),
                                reconnectDelay}
                );
                context.getReconnector()
                        .reconnect(doReconnect)
                        .delay(reconnectDelay, TimeUnit.MILLISECONDS);
            }
        };
    }

    private void createSubscriberClientAndSubscriptionHandler(final boolean willSubscribe) {
        final String mqttClientId = resolveMqttClientId(connection, mqttSpecificConfig);
        final var cancelReconnect = new AtomicBoolean(false);
        // apply last will config only if *no* separate publisher client is used
        final boolean applyLastWillConfig = !mqttSpecificConfig.separatePublisherClient();
        final String clientRole = mqttSpecificConfig.separatePublisherClient() ? CONSUMER : CONSUMER + "+" + PUBLISHER;
        final Q createdClient = getClientFactory().newClient(connection, mqttClientId, mqttConfig, mqttSpecificConfig,
                applyLastWillConfig,
                getMqttClientConnectedListener(clientRole),
                getMqttClientDisconnectedListener(clientRole, cancelReconnect),
                connectionLogger,
                connectivityConfig().getConnectionConfig().doubleDecodingEnabled());
        client = new ClientWithCancelSwitch(createdClient, cancelReconnect);

        if (willSubscribe) {
            // create a "real" subscription handler:
            subscriptionHandler = createSubscriptionHandler(connection, createdClient, logger);
        } else {
            subscriptionHandler = new DummySubscriptionHandler<>(connection, logger);
        }
    }

    private void resetClientAndSubscriptionHandler() {
        publisherClient = null;
        client = null;
        subscriptionHandler = null;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        final var connectionToBeTested = testConnectionCommand.getConnection();
        final var testMqttSpecificConfig = MqttSpecificConfig.fromConnection(connectionToBeTested, mqttConfig);
        final String mqttClientId = resolveMqttClientId(connectionToBeTested, testMqttSpecificConfig);
        // attention: do not use reconnect, otherwise the future never returns
        final Q testClient;
        try {
            testClient =
                    getClientFactory().newClient(connectionToBeTested, mqttClientId, mqttConfig, testMqttSpecificConfig,
                            false, connectionLogger, connectivityConfig().getConnectionConfig().doubleDecodingEnabled());
        } catch (final Exception e) {
            return CompletableFuture.completedFuture(new Status.Failure(e.getCause()));
        }
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(testConnectionCommand)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionToBeTested.getId());
        final AbstractMqttSubscriptionHandler<S, P, R> testSubscriptions =
                createSubscriptionHandler(connectionToBeTested, testClient, l);
        // always use clean session for tests to not have broker persist anything
        return sendConn(testClient, true, Duration.ZERO)
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
                        final var message = "Connection test for was successful";
                        connectionLogger.success(message);
                        l.info("Connection test for {} was successful", connectionId());
                        status = new Status.Success(message);
                    } else {
                        l.info("Connection test to {} failed: {}", connectionToBeTested.getUri(), t.getMessage());
                        connectionLogger.failure("Connection test failed: {0}", t.getMessage());
                        status = new Status.Failure(t);
                    }
                    stopCommandConsumers(testSubscriptions);
                    stopChildActor(publisherActor);
                    safelyDisconnectClient(new ClientWithCancelSwitch(testClient, new AtomicBoolean(false)), "test",
                            true);
                    return status;
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // start publisher and consumer actors first.
        // after that, subscribe and connect in parallel in order to receive redelivered PUBLISH messages.
        if (client == null) {
            createClientAndSubscriptionHandler(!connection.getSources().isEmpty());
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

        Patterns.pipe(subscribeAndSendConn(), getContext().getDispatcher())
                .to(getSelf(), clientConnected.getOrigin().orElseGet(ActorRef::noSender));

        return stay();
    }

    private CompletionStage<InitializationResult> subscribeAndSendConn() {
        // add subscriber streams right away, but this future won't complete until SUBACK arrive after CONNACK
        final CompletionStage<List<R>> subAckFuture = getSubscriptionHandler().subscribe();

        final CompletableFuture<InitializationResult> connAckFuture = sendConnAndExpectConnAck();

        return connAckFuture.thenCompose(connResult -> {
            if (connResult.isSuccess()) {
                // compose with subAckFuture only if connection was successful,
                // otherwise subAckFuture never completes!
                return subAckFuture
                        // discard subAck result and return connection result instead
                        .thenApply(l -> connResult);
            } else {
                // otherwise return the connection failure directly
                return connAckFuture;
            }
        }).exceptionally(InitializationResult::failed);
    }

    private CompletableFuture<InitializationResult> sendConnAndExpectConnAck() {
        final var mqttClient = getClient().getMqttClient();
        final CompletableFuture<?> publisherConnFuture = connectPublisherClient();
        return publisherConnFuture
                .thenCompose(unused -> {
                    final boolean cleanSession = mqttSpecificConfig.cleanSession();
                    final Duration keepAlive = mqttSpecificConfig.getKeepAliveInterval().orElse(null);
                    return sendConn(mqttClient, cleanSession, keepAlive);
                })
                .handle(this::handleConnAck);
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

    private CompletableFuture<?> connectPublisherClient() {
        if (publisherClient != null && client != publisherClient) {
            // if publisher client is separate, start with clean session.
            final Duration keepAlive = mqttSpecificConfig.getKeepAliveInterval().orElse(null);
            return sendConn(publisherClient.getMqttClient(), false, keepAlive).toCompletableFuture();
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
        checkNotNull(publisherClient, "publisherClient");
        publisherActor = startPublisherActor(connection(), publisherClient.getMqttClient());
        return CompletableFuture.completedFuture(DONE);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        final var publisherDisconnectFuture = publisherClient != null
                ? publisherClient.disconnect(true)
                : CompletableFuture.completedStage(null);
        final var consumerDisconnectFuture = client != null
                ? client.disconnect(true)
                : CompletableFuture.completedStage(null);
        final var disconnectFuture =
                publisherDisconnectFuture.thenCombine(consumerDisconnectFuture, (void1, void2) -> null)
                        .handle((aVoid, throwable) -> {
                            if (null != throwable) {
                                logger.info("Error while disconnecting: {}", throwable);
                            } else {
                                logger.debug("Successfully disconnected.");
                            }
                            return ClientDisconnected.of(origin, shutdownAfterDisconnect);
                        });
        Patterns.pipe(disconnectFuture, getContext().getDispatcher()).to(getSelf(), origin);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        stopCommandConsumers(subscriptionHandler);
        stopChildActor(publisherActor);
        safelyDisconnectClient(client, CONSUMER, true);
        safelyDisconnectClient(publisherClient, PUBLISHER, true);
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
     * @param clientToDisconnect the client to disconnect
     * @param name a name describing the client's purpose
     * @param preventAutomaticReconnect whether automatic reconnect should be disabled.
     */
    private void safelyDisconnectClient(@Nullable final ClientWithCancelSwitch clientToDisconnect,
            final String name, final boolean preventAutomaticReconnect) {
        if (clientToDisconnect != null) {
            logger.info("Disconnecting mqtt <{}> client, ignoring any errors.", name);
            clientToDisconnect.disconnect(preventAutomaticReconnect).exceptionally(error -> {
                final var cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof MqttClientStateException) {
                    logger.debug("Failed to disconnect client <{}>, it was probably already closed: {}",
                            clientToDisconnect.mqttClient, cause);
                } else {
                    logger.error(error, "Failed to disconnect client <{}>", clientToDisconnect.mqttClient);
                }
                return null;
            });
        }
    }

    private void startHiveMqConsumers(final Consumer<MqttConsumer> consumerListener) {
        connection().getSources().stream()
                .map(source -> MqttConsumer.of(source,
                        startConsumerActor(isDryRun(), source, getInboundMappingSink(), mqttSpecificConfig)))
                .forEach(consumerListener);
    }

    private void stopCommandConsumers(@Nullable final AbstractMqttSubscriptionHandler<S, P, R> subscriptionHandler) {
        if (subscriptionHandler != null) {
            subscriptionHandler.clearConsumerActors(this::stopChildActor);
        }
    }

    private ClientWithCancelSwitch getClient() {
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

    private String distinguishClientIdIfNecessary(final String configuredClientId) {
        if (configuredClientId.isEmpty()) {
            return configuredClientId;
        } else {
            return getClientId(configuredClientId);
        }
    }

    /*
     *  For MQTT connections only one Consumer Actor for all addresses is started.
     */
    @Override
    protected int determineNumberOfConsumers() {
        return connection.getSources()
                .stream()
                .mapToInt(Source::getConsumerCount)
                .sum();
    }

    /*
     *  For MQTT connections only one Consumer Actor for all addresses is started.
     */
    @Override
    protected Stream<String> getSourceAddresses() {
        return connection.getSources().stream()
                .map(Source::getAddresses)
                .map(sourceAddresses -> String.join(";", sourceAddresses));
    }

    static class MqttClientConnected extends AbstractWithOrigin implements ClientConnected {

        MqttClientConnected(@Nullable final ActorRef origin) {
            super(origin);
        }

        @Override
        public Optional<ActorRef> getOrigin() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + super.toString() + "]";
        }
    }

    enum Control {
        RECONNECT_CONSUMER_CLIENT,
        DO_RECONNECT_CONSUMER_CLIENT
    }

    /**
     * This class associates an mqtt client with an {@link java.util.concurrent.atomic.AtomicBoolean} that cancels
     * the automatic reconnect mechanism in case the client is not disconnected properly.
     */
    private final class ClientWithCancelSwitch {

        private final Q mqttClient;
        private final AtomicBoolean cancelReconnect;

        private ClientWithCancelSwitch(final Q mqttClient, final AtomicBoolean cancelReconnect) {
            this.mqttClient = mqttClient;
            this.cancelReconnect = cancelReconnect;
        }

        private Q getMqttClient() {
            return mqttClient;
        }

        private CompletionStage<Void> disconnect(final boolean preventAutomaticReconnect) {
            // cancel reconnecting before sending disconnect message
            cancelReconnect.set(preventAutomaticReconnect);
            return disconnectClient(mqttClient);
        }
    }

}
