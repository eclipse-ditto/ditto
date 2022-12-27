/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientData;
import org.eclipse.ditto.connectivity.service.messaging.ReportConnectionStatusError;
import org.eclipse.ditto.connectivity.service.messaging.ReportConnectionStatusSuccess;
import org.eclipse.ditto.connectivity.service.messaging.backoff.RetryTimeoutStrategy;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.ClientRole;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientConnectedListener;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientDisconnectedListener;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.NoMqttConnectionException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.MqttConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming.ReconnectConsumerClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing.MqttPublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.MqttSubscriber;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SubscribeResult;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.concurrent.ExecutionContextExecutor;

/**
 * Actor for handling connection to an MQTT broker for protocol versions 3 or 5.
 */
public final class MqttClientActor extends BaseClientActor {

    private final MqttConfig mqttConfig;
    private final MqttSpecificConfig mqttSpecificConfig;

    private final GenericMqttClientFactory genericMqttClientFactory;
    @Nullable private GenericMqttClient genericMqttClient;
    private final AtomicBoolean automaticReconnect;
    @Nullable private ActorRef publishingActorRef;
    private final List<ActorRef> mqttConsumerActorRefs;

    @SuppressWarnings("java:S1144") // called by reflection
    private MqttClientActor(final Connection connection,
            final ActorRef commandForwarder,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        super(connection, commandForwarder, connectionActor, dittoHeaders, connectivityConfigOverwrites);

        final var connectivityConfig = connectivityConfig();
        final var connectionConfig = connectivityConfig.getConnectionConfig();
        mqttConfig = connectionConfig.getMqttConfig();

        mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);

        genericMqttClientFactory = GenericMqttClientFactory.newInstance();
        genericMqttClient = null;
        automaticReconnect = new AtomicBoolean(true);
        publishingActorRef = null;
        mqttConsumerActorRefs = new ArrayList<>();
    }

    /**
     * Returns the {@code Props} for creating a {@code GenericMqttClientActor} with the specified arguments.
     *
     * @param mqttConnection the MQTT connection.
     * @param commandForwarder the actor used to send signals into the Ditto cluster.
     * @param connectionActor the connection persistence actor which creates the returned client actor.
     * @param dittoHeaders headers of the command that caused the returned client actor to be created.
     * @param connectivityConfigOverwrites the overwrites of the connectivity config for the given connection.
     * @return the Props.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props props(final Connection mqttConnection,
            final ActorRef commandForwarder,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        return Props.create(MqttClientActor.class,
                ConditionChecker.checkNotNull(mqttConnection, "mqttConnection"),
                ConditionChecker.checkNotNull(commandForwarder, "commandForwarder"),
                ConditionChecker.checkNotNull(connectionActor, "connectionActor"),
                ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders"),
                ConditionChecker.checkNotNull(connectivityConfigOverwrites, "connectivityConfigOverwrites"));
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        final FSMStateFunctionBuilder<BaseClientState, BaseClientData> result;
        if (isReconnectForRedelivery()) {
            result = super.inConnectingState()
                    .event(ReconnectConsumerClient.class, this::scheduleConsumerClientReconnect)
                    .eventEquals(Control.RECONNECT_CONSUMER_CLIENT, this::reconnectConsumerClient);
        } else {
            result = super.inConnectingState();
        }
        return result;
    }

    private boolean isReconnectForRedelivery() {
        return mqttSpecificConfig.reconnectForRedelivery();
    }

    private State<BaseClientState, BaseClientData> scheduleConsumerClientReconnect(
            final ReconnectConsumerClient reconnectConsumerClient,
            final BaseClientData baseClientData
    ) {
        final var trigger = Control.RECONNECT_CONSUMER_CLIENT;
        if (isTimerActive(trigger.name())) {
            logger.debug("Timer <{}> is active, thus not scheduling reconnecting consumer client again.",
                    trigger.name());
        } else {
            final var reconnectForRedeliveryDelay = reconnectConsumerClient.getReconnectDelay();
            logger.info("Scheduling reconnecting of consumer client in <{}>.", reconnectForRedeliveryDelay);
            startSingleTimer(trigger.name(), trigger, reconnectForRedeliveryDelay.getDuration());
        }
        return stay();
    }

    private State<BaseClientState, BaseClientData> reconnectConsumerClient(final Control control,
            final BaseClientData baseClientData) {

        if (null != genericMqttClient) {
            enableAutomaticReconnect();
            genericMqttClient.disconnectClientRole(ClientRole.CONSUMER);
        }
        return stay();
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        final FSMStateFunctionBuilder<BaseClientState, BaseClientData> result;
        if (isReconnectForRedelivery()) {
            result = super.inConnectedState()
                    .event(ReconnectConsumerClient.class, this::scheduleConsumerClientReconnect)
                    .eventEquals(Control.RECONNECT_CONSUMER_CLIENT, this::reconnectConsumerClient);
        } else {
            result = super.inConnectedState();
        }
        return result;
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCmd) {
        final var connectionTesterActorRef = childActorNanny.startChildActorConflictFree(
                ConnectionTesterActor.class.getSimpleName(),
                ConnectionTesterActor.props(connectivityConfig(),
                        this::getSshTunnelState,
                        connectionLogger,
                        actorUuid,
                        connectivityStatusResolver,
                        genericMqttClientFactory)
        );
        return Patterns.ask(connectionTesterActorRef, testConnectionCmd, ConnectionTesterActor.ASK_TIMEOUT)
                .handle((response, throwable) -> {
                    final Status.Status result;
                    if (null == throwable) {
                        result = new Status.Success(response);
                    } else if (throwable instanceof CompletionException completionException) {
                        result = new Status.Failure(completionException.getCause());
                    } else {
                        result = new Status.Failure(throwable);
                    }
                    return result;
                })
                .whenComplete((status, error) -> connectionTesterActorRef.tell(PoisonPill.getInstance(), getSelf()));
    }

    @Override
    protected CompletionStage<Void> stopConsuming() {
        if (genericMqttClient == null) {
            return CompletableFuture.completedStage(null);
        } else {
            final var mqttTopicFilters =
                    getSourceAddresses().map(MqttTopicFilter::of).toArray(MqttTopicFilter[]::new);
            return genericMqttClient.unsubscribe(mqttTopicFilters);
        }
    }

    @Override
    protected void cleanupResourcesForConnection() {
        mqttConsumerActorRefs.forEach(this::stopChildActor);
        stopChildActor(publishingActorRef);
        if (null != genericMqttClient) {
            disableAutomaticReconnect();
            genericMqttClient.disconnect();
        }

        genericMqttClient = null;
        publishingActorRef = null;
        mqttConsumerActorRefs.clear();
    }

    private void disableAutomaticReconnect() {
        automaticReconnect.set(false);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        if (null == genericMqttClient) {
            genericMqttClient =
                    genericMqttClientFactory.getGenericMqttClient(getHiveMqttClientPropertiesOrThrow(connection));
            enableAutomaticReconnect();
        }
        Patterns.pipe(
                genericMqttClient.connect().thenApply(aVoid -> MqttClientConnected.of(origin)),
                getContextDispatcher()
        ).to(getSelf());
    }

    private HiveMqttClientProperties getHiveMqttClientPropertiesOrThrow(final Connection connection) {
        try {
            return HiveMqttClientProperties.builder()
                    .withMqttConnection(connection)
                    .withConnectivityConfig(connectivityConfig())
                    .withMqttSpecificConfig(mqttSpecificConfig)
                    .withSshTunnelStateSupplier(this::getSshTunnelState)
                    .withConnectionLogger(connectionLogger)
                    .withActorUuid(actorUuid)
                    .withClientConnectedListener(getClientConnectedListener())
                    .withClientDisconnectedListener(getClientDisconnectedListener())
                    .build();
        } catch (final NoMqttConnectionException e) {

            // Let the supervisor strategy take care. Should not happen anyway.
            throw new IllegalArgumentException(e);
        }
    }

    private GenericMqttClientConnectedListener getClientConnectedListener() {
        return (context, clientRole) -> {
            logger.info("Connected client <{}>.",
                    getClientId(clientRole, getMqttClientIdentifierOrNull(context.getClientConfig())));
            getSelf().tell(new ReportConnectionStatusSuccess(), ActorRef.noSender());
        };
    }

    @Nullable
    private static MqttClientIdentifier getMqttClientIdentifierOrNull(final MqttClientConfig mqttClientConfig) {
        return mqttClientConfig.getClientIdentifier().orElse(null);
    }

    private static String getClientId(final ClientRole clientRole,
            @Nullable final MqttClientIdentifier mqttClientIdentifier) {

        return MessageFormat.format("{0}:{1}", clientRole, mqttClientIdentifier);
    }

    private GenericMqttClientDisconnectedListener getClientDisconnectedListener() {
        return (context, clientRole) -> {
            final var mqttClientReconnector = context.getReconnector();
            final var retryTimeoutStrategy = getRetryTimeoutStrategy();

            if (0 == mqttClientReconnector.getAttempts()) {
                retryTimeoutStrategy.reset();
            }

            final var clientId = getClientId(clientRole, getMqttClientIdentifierOrNull(context.getClientConfig()));
            if (isMqttClientInConnectingState(context.getClientConfig())) {

                /*
                 * If the client is in initial CONNECTING state (i.e. was never
                 * connected, not reconnecting), we disable the automatic
                 * reconnect because the client would continue to connect and
                 * the caller would never see the cause why the connection
                 * failed.
                 */
                logger.info("Initial connect of client <{}> failed. Disabling automatic reconnect.", clientId);
                mqttClientReconnector.reconnect(false);
                getSelf().tell(ConnectionFailure.of(null, context.getCause(), "MQTT client got disconnected."),
                        ActorRef.noSender());
            } else {
                final var mqttDisconnectSource = context.getSource();
                final var reconnect = isReconnect();
                final var reconnectDelay = getReconnectDelay(retryTimeoutStrategy, mqttDisconnectSource);
                logger.info("Client <{}> disconnected by <{}>.", clientId, mqttDisconnectSource);
                if (reconnect) {
                    logger.info("Reconnecting client <{}> with current tries <{}> and a delay of <{}>.",
                            clientId,
                            retryTimeoutStrategy.getCurrentTries(),
                            reconnectDelay);

                    // This is sent because the status of the client isn't made explicit to the user.
                    getSelf().tell(new ReportConnectionStatusError(context.getCause()), ActorRef.noSender());
                } else if (List.of(MqttDisconnectSource.CLIENT, MqttDisconnectSource.SERVER)
                        .contains(context.getSource())) {
                    logger.info("Not reconnecting client <{}> after disconnect caused by: {}.", clientId,
                            context.getCause());
                    getSelf().tell(ConnectionFailure.of(null, context.getCause(), "MQTT client got disconnected."),
                            ActorRef.noSender());
                } else if (MqttDisconnectSource.USER.equals(context.getSource())) {
                    logger.debug("Not reconnecting client <{}>, user initiated disconnect: {}.", clientId,
                            context.getCause());
                } else {
                    logger.info("Not reconnecting client <{}>: {}.", clientId, context.getCause());
                }
                mqttClientReconnector.delay(reconnectDelay.toMillis(), TimeUnit.MILLISECONDS);
                mqttClientReconnector.reconnect(reconnect);
            }
        };
    }

    private RetryTimeoutStrategy getRetryTimeoutStrategy() {
        final var reconnectBackOffConfig = mqttConfig.getReconnectBackOffConfig();
        return RetryTimeoutStrategy.newDuplicationRetryTimeoutStrategy(reconnectBackOffConfig.getTimeoutConfig());
    }

    private static boolean isMqttClientInConnectingState(final MqttClientConfig mqttClientConfig) {
        return MqttClientState.CONNECTING == mqttClientConfig.getState();
    }

    private boolean isReconnect() {
        final var connection = connection();
        return connection.isFailoverEnabled() && automaticReconnect.get();
    }

    private Duration getReconnectDelay(final RetryTimeoutStrategy retryTimeoutStrategy,
            final MqttDisconnectSource mqttDisconnectSource) {
        final Duration result;
        final var retryTimeoutReconnectDelay = retryTimeoutStrategy.getNextTimeout();
        if (MqttDisconnectSource.SERVER == mqttDisconnectSource) {
            // wait at least the configured duration for server initiated disconnect to not overload the server with reconnect attempts
            result = getMaxDuration(retryTimeoutReconnectDelay,
                    mqttConfig.getReconnectMinTimeoutForMqttBrokerInitiatedDisconnect());
        } else {
            result = retryTimeoutReconnectDelay;
        }
        return result;
    }

    private static Duration getMaxDuration(final Duration d1, final Duration d2) {
        return d1.compareTo(d2) >= 0 ? d1 : d2;
    }

    private void enableAutomaticReconnect() {
        automaticReconnect.set(true);
    }

    private ExecutionContextExecutor getContextDispatcher() {
        final var actorContext = getContext();
        return actorContext.getDispatcher();
    }

    @Override
    protected void doDisconnectClient(final Connection connection,
            @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {

        final CompletionStage<Void> disconnectFuture;
        if (null == genericMqttClient) {
            disconnectFuture = CompletableFuture.completedFuture(null);
        } else {
            disableAutomaticReconnect();
            disconnectFuture = genericMqttClient.disconnect();
        }

        Patterns.pipe(
                disconnectFuture.handle((aVoid, throwable) -> ClientDisconnected.of(origin, shutdownAfterDisconnect)),
                getContextDispatcher()
        ).to(getSelf(), origin);
    }

    @Nullable
    @Override
    protected ActorRef getPublisherActor() {
        return publishingActorRef;
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        final CompletionStage<Status.Status> result;
        if (null != genericMqttClient) {
            publishingActorRef = startChildActorConflictFree(
                    MqttPublisherActor.class.getSimpleName(),
                    MqttPublisherActor.propsProcessing(connection(),
                            connectivityStatusResolver,
                            connectivityConfig(),
                            genericMqttClient)
            );
            result = CompletableFuture.completedFuture(DONE);
        } else {
            result = CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot start publisher actor because generic MQTT client is null.")
            );
        }
        return result;
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        return subscribe()
                .thenCompose(this::handleSourceSubscribeResults)
                .thenApply(actorRefs -> {
                    mqttConsumerActorRefs.addAll(actorRefs);
                    return DONE;
                });
    }

    private CompletionStage<Source<SubscribeResult, NotUsed>> subscribe() {
        final CompletionStage<Source<SubscribeResult, NotUsed>> result;
        if (null != genericMqttClient) {
            final var subscriber = MqttSubscriber.newInstance(genericMqttClient);
            result = CompletableFuture.completedFuture(
                    subscriber.subscribeForConnectionSources(connection().getSources())
            );
        } else {
            result = CompletableFuture.failedFuture(new IllegalStateException(
                    "Cannot subscribe for connection sources as generic MQTT client is not yet initialised."
            ));
        }
        return result;
    }

    private CompletionStage<List<ActorRef>> handleSourceSubscribeResults(
            final Source<SubscribeResult, NotUsed> sourceSubscribeResults
    ) {
        return sourceSubscribeResults.map(this::startMqttConsumerActorOrThrow)
                .toMat(Sink.seq(), Keep.right())
                .run(getContext().getSystem());
    }

    private ActorRef startMqttConsumerActorOrThrow(final SubscribeResult subscribeResult) {
        if (subscribeResult.isSuccess()) {
            return startChildActorConflictFree(
                    MqttConsumerActor.class.getSimpleName(),
                    MqttConsumerActor.propsProcessing(connection(),
                            getInboundMappingSink(),
                            subscribeResult.getConnectionSource(),
                            connectivityStatusResolver,
                            connectivityConfig(),
                            subscribeResult.getMqttPublishSourceOrThrow())
            );
        } else {
            throw subscribeResult.getErrorOrThrow();
        }
    }

    /*
     * For MQTT connections only one consumer actor for all addresses is started,
     * i.e. one consumer actor per connection source.
     */
    @Override
    protected int determineNumberOfConsumers() {
        return connectionSources()
                .mapToInt(org.eclipse.ditto.connectivity.model.Source::getConsumerCount)
                .sum();
    }

    private Stream<org.eclipse.ditto.connectivity.model.Source> connectionSources() {
        return connection().getSources().stream();
    }

    /*
     * For MQTT connections only one Consumer Actor for all addresses is started,
     * i.e. one consumer actor per connection source.
     */
    @Override
    protected Stream<String> getSourceAddresses() {
        return connectionSources()
                .map(org.eclipse.ditto.connectivity.model.Source::getAddresses)
                .map(sourceAddresses -> String.join(";", sourceAddresses));
    }

    @Override
    public void postStop() {
        logger.info("Actor stopped, stopping clients.");
        if (null != genericMqttClient) {
            disableAutomaticReconnect();
            genericMqttClient.disconnect();
        }
        super.postStop();
    }

    private enum Control {

        RECONNECT_CONSUMER_CLIENT

    }

}
