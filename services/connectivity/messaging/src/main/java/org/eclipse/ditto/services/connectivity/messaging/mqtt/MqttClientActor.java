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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MqttConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Graph;
import akka.stream.KillSwitches;
import akka.stream.Outlet;
import akka.stream.SharedKillSwitch;
import akka.stream.SinkShape;
import akka.stream.UniformFanOutShape;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import scala.util.Either;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class MqttClientActor extends BaseClientActor {

    private SharedKillSwitch consumerKillSwitch;
    private ActorRef mqttPublisherActor;

    private final Map<String, ActorRef> consumerByActorNameWithIndex;
    private final Set<ActorRef> pendingStatusReportsFromStreams;
    private final BiFunction<Connection, DittoHeaders, MqttConnectionFactory> connectionFactoryCreator;
    private final int sourceBufferSize;

    private CompletableFuture<Status.Status> testConnectionFuture = null;

    @SuppressWarnings("unused")
    MqttClientActor(final Connection connection,
            final ConnectivityStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder,
            final BiFunction<Connection, DittoHeaders, MqttConnectionFactory> connectionFactoryCreator) {

        super(connection, desiredConnectionStatus, conciergeForwarder);
        this.connectionFactoryCreator = connectionFactoryCreator;
        consumerByActorNameWithIndex = new HashMap<>();
        pendingStatusReportsFromStreams = new HashSet<>();

        final ConnectionConfig connectionConfig = connectivityConfig.getConnectionConfig();
        final MqttConfig mqttConfig = connectionConfig.getMqttConfig();
        sourceBufferSize = mqttConfig.getSourceBufferSize();
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private MqttClientActor(final Connection connection,
            final ConnectivityStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {

        this(connection, desiredConnectionStatus, conciergeForwarder, MqttConnectionFactory::of);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {

        return Props.create(MqttClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        // nothing to do so far
        return connection;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inTestingState() {
        return super.inTestingState()
                .event(Status.Status.class, (e, d) -> !Objects.equals(getSender(), getSelf()),
                        this::handleStatusReportFromChildren)
                .event(ClientConnected.class, BaseClientData.class, (event, data) -> {
                    final String url = data.getConnection().getUri();
                    final String message = "mqtt connection to " + url + " established successfully";
                    completeTestConnectionFuture(new Status.Success(message), data);
                    return stay();
                })
                .event(ConnectionFailure.class, BaseClientData.class, (event, data) -> {
                    completeTestConnectionFuture(new Status.Failure(event.getFailure().cause()), data);
                    return stay();
                });
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return super.inConnectingState()
                .event(Status.Status.class, this::handleStatusReportFromChildren);
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        if (testConnectionFuture != null) {
            final Exception error = new IllegalStateException("test future exists");
            return CompletableFuture.completedFuture(new Status.Failure(error));
        }
        testConnectionFuture = new CompletableFuture<>();
        connectClient(connection, true);
        return testConnectionFuture;
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers started already.
    }

    @Override
    protected ActorRef getPublisherActor() {
        return mqttPublisherActor;
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        connectClient(connection, false);
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        self().tell((ClientDisconnected) () -> null, origin);
    }

    /**
     * Start MQTT publisher and subscribers, expect "Status.Success" from each of them, then send "ClientConnected" to
     * self.
     *
     * @param connection connection of the publisher and subscribers.
     * @param dryRun if set to true, exchange no message between the broker and the Ditto cluster.
     */
    private void connectClient(final Connection connection, final boolean dryRun) {
        final MqttConnectionFactory factory =
                connectionFactoryCreator.apply(connection, stateData().getSessionHeaders());

        // start publisher
        startMqttPublisher(factory, dryRun);

        // start message mapping processor actor early so that consumer streams can be run.
        // note that it has to be started after the publisher since the publisher actor is needed inside the mapping processor
        final Either<DittoRuntimeException, ActorRef> messageMappingProcessor = startMessageMappingProcessor();

        // start consumers
        if (isConsuming()) {
            if (messageMappingProcessor.isLeft()) {
                final DittoRuntimeException e = messageMappingProcessor.left().get();
                log.warning("failed to start mapping processor due to {}", e);
            } else {
                final ActorRef mappingActor = messageMappingProcessor.right().get();
                // start new KillSwitch for the next batch of consumers
                refreshConsumerKillSwitch(KillSwitches.shared("consumerKillSwitch"));
                connection().getSources().forEach(source ->
                        startMqttConsumers(factory, mappingActor, source, dryRun));
            }
        } else {
            log.info("Not starting consumption because there is no source.");
        }
    }

    private void startMqttPublisher(final MqttConnectionFactory factory, final boolean dryRun) {
        log.info("Starting MQTT publisher actor.");
        // ensure no previous publisher stays in memory
        stopMqttPublisher();
        mqttPublisherActor = startChildActorConflictFree(MqttPublisherActor.ACTOR_NAME,
                MqttPublisherActor.props(connectionId(), getTargetsOrEmptyList(), factory, getSelf(), dryRun));
        pendingStatusReportsFromStreams.add(mqttPublisherActor);
    }

    private void startMqttConsumers(final MqttConnectionFactory factory,
            final ActorRef messageMappingProcessorActor,
            final Source source,
            final boolean dryRun) {

        if (source.getConsumerCount() <= 0) {
            log.info("source #{} has {} consumer - not starting stream", source.getIndex(), source.getConsumerCount());
            return;
        }

        for (int i = 0; i < source.getConsumerCount(); i++) {

            log.debug("Starting {}. consumer actor for source <{}> on connection <{}>.", i, source.getIndex(),
                    factory.connectionId());

            final String uniqueSuffix = getUniqueSourceSuffix(source.getIndex(), i);
            final String actorNamePrefix = MqttConsumerActor.ACTOR_NAME_PREFIX + uniqueSuffix;

            final Props mqttConsumerActorProps =
                    MqttConsumerActor.props(connectionId(), messageMappingProcessorActor,
                            source.getAuthorizationContext(),
                            source.getEnforcement().orElse(null),
                            dryRun, String.join(";", source.getAddresses()));
            final ActorRef mqttConsumerActor = startChildActorConflictFree(actorNamePrefix, mqttConsumerActorProps);

            consumerByActorNameWithIndex.put(actorNamePrefix, mqttConsumerActor);
        }

        // failover implemented by factory
        final akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> mqttStreamSource =
                factory.newSource(source, sourceBufferSize);

        final Graph<SinkShape<MqttMessage>, NotUsed> consumerLoadBalancer =
                createConsumerLoadBalancer(consumerByActorNameWithIndex.values());

        final CompletionStage<Done> subscriptionInitialized =
                mqttStreamSource.viaMat(consumerKillSwitch.flow(), Keep.left())
                        .toMat(consumerLoadBalancer, Keep.left())
                        .run(ActorMaterializer.create(getContext()));

        // consumerCount > 0 because the early return did not trigger
        final ActorRef firstConsumer = consumerByActorNameWithIndex.values().iterator().next();
        final ActorRef self = getSelf();
        pendingStatusReportsFromStreams.add(firstConsumer);
        subscriptionInitialized.handle((done, error) -> {
            final Collection<String> sourceAddresses = source.getAddresses();
            if (error == null) {
                connectionLogger.success("Subscriptions {0} initialized successfully.", sourceAddresses);
                log.info("Subscriptions {} initialized successfully", sourceAddresses);
                self.tell(new Status.Success(done), firstConsumer);
            } else {
                log.info("Subscriptions {} failed due to {}: {}", sourceAddresses,
                        error.getClass().getCanonicalName(), error.getMessage());
                self.tell(new ImmutableConnectionFailure(null, error, "subscriptions"), firstConsumer);
            }
            return done;
        });
    }

    private static String getUniqueSourceSuffix(final int sourceIndex, final int consumerIndex) {
        return sourceIndex + "-" + consumerIndex;
    }

    @Override
    protected void cleanupResourcesForConnection() {
        pendingStatusReportsFromStreams.clear();
        activateConsumerKillSwitch();
        stopCommandConsumers();
        stopMessageMappingProcessorActor();
        stopMqttPublisher();
    }

    private void activateConsumerKillSwitch() {
        refreshConsumerKillSwitch(null);
    }

    private void refreshConsumerKillSwitch(@Nullable final SharedKillSwitch nextKillSwitch) {
        if (consumerKillSwitch != null) {
            log.info("Closing consumers stream.");
            consumerKillSwitch.shutdown();
        }
        consumerKillSwitch = nextKillSwitch;
    }

    private void stopMqttPublisher() {
        if (mqttPublisherActor != null) {
            stopChildActor(mqttPublisherActor);
            mqttPublisherActor = null;
        }
    }

    private void stopCommandConsumers() {
        consumerByActorNameWithIndex.forEach((actorNamePrefix, child) -> stopChildActor(child));
        consumerByActorNameWithIndex.clear();
    }

    private FSM.State<BaseClientState, BaseClientData> handleStatusReportFromChildren(final Status.Status status,
            final BaseClientData data) {

        if (pendingStatusReportsFromStreams.contains(getSender())) {
            pendingStatusReportsFromStreams.remove(getSender());
            if (status instanceof Status.Failure) {
                final Status.Failure failure = (Status.Failure) status;
                final ConnectionFailure connectionFailure =
                        new ImmutableConnectionFailure(null, failure.cause(), "child failed");
                getSelf().tell(connectionFailure, ActorRef.noSender());
            } else if (pendingStatusReportsFromStreams.isEmpty()) {
                // all children are ready; this client actor is connected.
                getSelf().tell((ClientConnected) () -> null, ActorRef.noSender());
            }
        }
        return stay();
    }

    private void completeTestConnectionFuture(final Status.Status testResult, final BaseClientData data) {
        if (testConnectionFuture != null) {
            testConnectionFuture.complete(testResult);
        } else {
            // no future; test failed.
            final Exception exception = new IllegalStateException("test future not found");
            getSelf().tell(new Status.Failure(exception), getSelf());
        }
    }

    private static <T> Graph<SinkShape<T>, NotUsed> createConsumerLoadBalancer(final Collection<ActorRef> routees) {
        return GraphDSL.create(builder -> {
            final UniformFanOutShape<T, T> loadBalancer = builder.add(Balance.create(routees.size()));
            int i = 0;
            for (final ActorRef routee : routees) {
                final Sink<Object, NotUsed> sink = Sink.actorRefWithAck(routee,
                        ConsumerStreamMessage.STREAM_STARTED,
                        ConsumerStreamMessage.STREAM_ACK,
                        ConsumerStreamMessage.STREAM_ENDED,
                        error -> ConsumerStreamMessage.STREAM_ENDED);
                final SinkShape<Object> sinkShape = builder.add(sink);
                final Outlet<T> outlet = loadBalancer.out(i++);
                builder.from(outlet).to(sinkShape);
            }
            return SinkShape.of(loadBalancer.in());
        });
    }

    enum ConsumerStreamMessage {

        STREAM_STARTED,

        STREAM_ACK,

        /**
         * Message for stream completion and stream failure.
         */
        STREAM_ENDED

    }

}
