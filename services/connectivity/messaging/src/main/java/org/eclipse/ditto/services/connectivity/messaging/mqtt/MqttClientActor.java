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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Graph;
import akka.stream.KillSwitches;
import akka.stream.Outlet;
import akka.stream.SharedKillSwitch;
import akka.stream.SinkShape;
import akka.stream.UniformFanOutShape;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.MqttSourceSettings;
import akka.stream.alpakka.mqtt.javadsl.MqttSource;
import akka.stream.javadsl.Balance;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import scala.collection.JavaConverters;
import scala.util.Either;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public class MqttClientActor extends BaseClientActor {

    private SharedKillSwitch consumerKillSwitch;
    private ActorRef mqttPublisherActor;

    private final Map<String, ActorRef> consumerByActorNameWithIndex;
    private final Set<ActorRef> pendingStatusReportsFromStreams;

    private MqttClientActor(final Connection connection,
            final ConnectionStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {
        super(connection, desiredConnectionStatus, conciergeForwarder);
        consumerByActorNameWithIndex = new HashMap<>();
        pendingStatusReportsFromStreams = new HashSet<>();
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
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return super.inConnectingState()
                .event(Status.Status.class, this::handleStatusReportFromChildren);
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return super.inConnectedState()
                .event(CountPublishedMqttMessage.class, (message, data) -> {
                    incrementPublishedMessageCounter();
                    return stay();
                })
                .event(CountConsumedMqttMessage.class, (message, data) -> {
                    incrementConsumedMessageCounter();
                    return stay();
                });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();

        // cannot create test client in try-with-resources because it should run asynchronously not to block the actor
        MqttAsyncClient testClient = null;
        try {
            testClient = new MqttAsyncClient(connection.getUri(), connection.getId(), new MemoryPersistence());
            final MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setConnectionTimeout(0);
            testClient.connect(mqttConnectOptions, new TestActionListener(testClient, connection, log, future));
        } catch (final MqttException e) {
            log.info("MQTT test client failed due to {}: {}", e.getClass().getCanonicalName(), e.getMessage());
            future.complete(new Status.Failure(e));
            forceCloseTestClient(testClient, log);
        }
        return future;
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here; publisher and consumers started already.
    }

    @Override
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(mqttPublisherActor);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final MqttConnectionSettings connectionSettings =
                MqttConnectionSettingsFactory.getInstance().createMqttConnectionSettings(connection());

        // start publisher
        startMqttPublisher(connectionSettings);

        // start consumers
        if (isConsuming()) {
            // start message mapping processor actor early so that consumer streams can be run
            final Either<DittoRuntimeException, ActorRef> messageMappingProcessor = startMessageMappingProcessor();
            if (messageMappingProcessor.isLeft()) {
                final DittoRuntimeException e = messageMappingProcessor.left().get();
                log.warning("failed to start mapping processor due to {}", e);
            } else {
                final ActorRef mappingActor = messageMappingProcessor.right().get();
                // start new KillSwitch for the next batch of consumers
                refreshConsumerKillSwitch(KillSwitches.shared("consumerKillSwitch"));
                connection().getSources()
                        .forEach(source -> startMqttConsumers(connectionSettings, mappingActor, source));
            }
        } else {
            log.info("Not starting consumption because there is no source.");
        }
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        self().tell((ClientDisconnected) () -> null, origin);
    }

    @Override
    protected CompletionStage<Map<String, AddressMetric>> getSourceConnectionStatus(final Source source) {
        return collectAsList(IntStream.range(0, source.getConsumerCount())
                .mapToObj(idx -> {
                    final String topics = String.join(",", source.getAddresses());
                    final String actorLabel =
                            MqttConsumerActor.ACTOR_NAME_PREFIX + getUniqueSourceSuffix(source.getIndex(), idx);
                    final ActorRef consumer = consumerByActorNameWithIndex.get(actorLabel);
                    return retrieveAddressMetric(topics, actorLabel, consumer);
                }))
                .thenApply(entries -> entries.stream().collect(Collectors.toMap(Pair::first, Pair::second)));
    }

    @Override
    protected CompletionStage<Map<String, AddressMetric>> getTargetConnectionStatus(final Target target) {
        final CompletionStage<Pair<String, AddressMetric>> targetEntryFuture =
                retrieveAddressMetric(target.getAddress(), MqttPublisherActor.ACTOR_NAME,
                        getPublisherActor().orElse(null));

        return targetEntryFuture.thenApply(targetEntry ->
                Collections.singletonMap(targetEntry.first(), targetEntry.second()));
    }

    private void startMqttPublisher(final MqttConnectionSettings connectionSettings) {
        log.info("Starting MQTT publisher actor.");
        // ensure no previous publisher stays in memory
        stopMqttPublisher();
        final MqttConnectionSettings publisherSettings = connectionSettings.withClientId(connectionId() + "-publisher");
        mqttPublisherActor = startChildActorConflictFree(MqttPublisherActor.ACTOR_NAME,
                MqttPublisherActor.props(publisherSettings, getSelf()));
        pendingStatusReportsFromStreams.add(mqttPublisherActor);
    }

    private void startMqttConsumers(final MqttConnectionSettings connectionSettings,
            final ActorRef messageMappingProcessorActor,
            final Source source) {

        if (source.getConsumerCount() <= 0) {
            log.info("source #{} has {} consumer - not starting stream", source.getIndex(), source.getConsumerCount());
            return;
        }

        final List<Pair<String, MqttQoS>> subscriptions = new ArrayList<>();
        final int qos = ((org.eclipse.ditto.model.connectivity.MqttSource) source).getQos();
        final MqttQoS mqttQos = MqttValidator.getQoS(qos);
        source.getAddresses()
                .forEach(sourceAddress -> subscriptions.add(Pair.create(sourceAddress, mqttQos)));

        for (int i = 0; i < source.getConsumerCount(); i++) {

            log.debug("Starting {}. consumer actor for source <{}> on connection <{}>.", i, source.getIndex(),
                    connectionId());

            final String uniqueSuffix = getUniqueSourceSuffix(source.getIndex(), i);
            final String actorNamePrefix = MqttConsumerActor.ACTOR_NAME_PREFIX + uniqueSuffix;

            final Props mqttConsumerActorProps =
                    MqttConsumerActor.props(messageMappingProcessorActor, source.getAuthorizationContext());
            final ActorRef mqttConsumerActor = startChildActorConflictFree(actorNamePrefix, mqttConsumerActorProps);

            consumerByActorNameWithIndex.put(actorNamePrefix, mqttConsumerActor);
        }

        final String clientId = connectionId() + "-source" + source.getIndex();
        final MqttSourceSettings settings =
                MqttSourceSettings.create(connectionSettings.withClientId(clientId))
                        .withSubscriptions(JavaConverters.asScalaBuffer(subscriptions).toSeq());
        // TODO make configurable
        final Integer bufferSize = 8;
        final akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> mqttSource =
                createMqttSource(settings, bufferSize);

        // TODO use RestartSource
//            final akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> restartingSource =
//                    wrapWithAsRestartSource(() -> mqttSource);
//                        .mapAsync(1, cm -> cm.messageArrivedComplete().thenApply(unused2 -> cm.message()))
//                        .take(input.size())

        final Graph<SinkShape<MqttMessage>, NotUsed> consumerLoadBalancer =
                createConsumerLoadBalancer(consumerByActorNameWithIndex.values());

        final CompletionStage<Done> subscriptionInitialized = mqttSource.viaMat(consumerKillSwitch.flow(), Keep.left())
                .map(this::countConsumedMqttMessage)
                .toMat(consumerLoadBalancer, Keep.left())
                .run(ActorMaterializer.create(getContext()));

        // consumerCount > 0 because the early return did not trigger
        final ActorRef firstConsumer = consumerByActorNameWithIndex.values().iterator().next();
        final ActorRef self = getSelf();
        pendingStatusReportsFromStreams.add(firstConsumer);
        subscriptionInitialized.handle((done, error) -> {
            if (error == null) {
                log.info("Subscriptions {} initialized successfully", subscriptions);
                self.tell(new Status.Success(done), firstConsumer);
            } else {
                log.info("Subscriptions {} failed due to {}: {}", subscriptions,
                        error.getClass().getCanonicalName(), error.getMessage());
                self.tell(new ImmutableConnectionFailure(null, error, "subscriptions"), firstConsumer);
            }
            return done;
        });
    }

    private <T> T countConsumedMqttMessage(final T mqttMessage) {
        getSelf().tell(new CountConsumedMqttMessage(), ActorRef.noSender());
        return mqttMessage;
    }

    private String getUniqueSourceSuffix(final int sourceIndex, final int consumerIndex) {
        return sourceIndex + "-" + consumerIndex;
    }

    // TODO: keep RestartSource or not?
    private <M> akka.stream.javadsl.Source<M, CompletionStage<Done>> wrapWithAsRestartSource(
            final Creator<akka.stream.javadsl.Source<M, CompletionStage<Done>>> source) {
        // makes use of the fact that these sources materialize a CompletionStage<Done>
        final CompletableFuture<Done> fut = new CompletableFuture<>();
        return RestartSource.withBackoff(
                Duration.ofMillis(100),
                Duration.ofSeconds(3),
                0.2d, // randomFactor
                5, // maxRestarts,
                () ->
                        source
                                .create()
                                .mapMaterializedValue(
                                        mat ->
                                                mat.handle(
                                                        (done, exception) -> {
                                                            if (done != null) {
                                                                fut.complete(done);
                                                            } else {
                                                                fut.completeExceptionally(exception);
                                                            }
                                                            return fut.toCompletableFuture();
                                                        })))
                .mapMaterializedValue(ignore -> fut.toCompletableFuture());
    }

    @Override
    protected void cleanupResourcesForConnection() {

        pendingStatusReportsFromStreams.clear();
        activateConsumerKillSwitch();
        stopCommandConsumers();
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

    private static akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> createMqttSource(
            final MqttSourceSettings settings,
            final int bufferSize) {

        return MqttSource.atMostOnce(settings, bufferSize);
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
                builder.from(outlet).to(sinkShape); // TODO: check if this generates "unchecked" warning on Sonatype
            }
            return SinkShape.of(loadBalancer.in());
        });
    }

    private static void forceCloseTestClient(@Nullable final MqttAsyncClient testClient,
            final DiagnosticLoggingAdapter log) {
        try {
            if (testClient != null) {
                if (testClient.isConnected()) {
                    testClient.disconnectForcibly();
                }
                testClient.close(true);
            }
        } catch (final MqttException closingException) {
            log.error(closingException, "MQTT test client failed to close!");
        }
    }

    /**
     * Self message to increment published message counter.
     */
    static final class CountPublishedMqttMessage {}

    /**
     * Self message to increment consumed message counter.
     */
    private static final class CountConsumedMqttMessage {}

    /**
     * Callback for connection tests.
     */
    private static final class TestActionListener implements IMqttActionListener {

        private final MqttAsyncClient client;
        private final Connection connection;
        private final DiagnosticLoggingAdapter log;
        private final CompletableFuture<Status.Status> future;

        private TestActionListener(final MqttAsyncClient client,
                final Connection connection,
                final DiagnosticLoggingAdapter log,
                final CompletableFuture<Status.Status> future) {

            this.client = client;
            this.connection = connection;
            this.log = log;
            this.future = future;
        }

        @Override
        public void onSuccess(final IMqttToken asyncActionToken) {
            forceCloseTestClient(client, log);
            final String message = "mqtt connection to " + connection.getUri() + " established successfully";
            future.complete(new Status.Success(message));
        }

        @Override
        public void onFailure(final IMqttToken asyncActionToken, final Throwable e) {
            forceCloseTestClient(client, log);
            log.info("MQTT test client failed due to {}: {}", e.getClass().getCanonicalName(), e.getMessage());
            future.complete(new Status.Failure(e));
        }
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
