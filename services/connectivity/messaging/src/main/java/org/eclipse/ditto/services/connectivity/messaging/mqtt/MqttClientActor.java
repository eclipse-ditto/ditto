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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

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
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitches;
import akka.stream.OverflowStrategy;
import akka.stream.SharedKillSwitch;
import akka.stream.UniqueKillSwitch;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.MqttSourceSettings;
import akka.stream.alpakka.mqtt.javadsl.MqttSink;
import akka.stream.alpakka.mqtt.javadsl.MqttSource;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import scala.collection.JavaConverters;

public class MqttClientActor extends BaseClientActor {

    static final Object COMPLETE_MESSAGE = new Object();
    private final ActorMaterializer materializer;
    private SharedKillSwitch consumerKillSwitch;
    private UniqueKillSwitch publisherKillSwitch;
    private ActorRef mqttPublisherActor;

    private final Map<String, ActorRef> consumerByActorNameWithIndex;

    private MqttClientActor(final Connection connection,
            final ConnectionStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {
        super(connection, desiredConnectionStatus, conciergeForwarder);
        materializer = ActorMaterializer.create(getContext().getSystem());
        consumerByActorNameWithIndex = new HashMap<>();
    }

    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {
        return Props.create(MqttClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        // nothing to do so far
        return connection;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inAnyState() {
        return super.inAnyState()
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
        final MqttConnectionSettings connectionSettings =
                MqttConnectionSettingsFactory.getInstance().createMqttConnectionSettings(connection());

        final Optional<ActorRef> mappingActorOptional = getMessageMappingProcessorActor();
        if (isConsuming()) {
            if (mappingActorOptional.isPresent()) {
                connection()
                        .getSources()
                        .forEach(source -> startMqttConsumers(connectionSettings, mappingActorOptional.get(), source));
            } else {
                log.warning("No message mapper available, not starting consumption.");
            }
        } else {
            log.debug("This connection is not consuming, not starting any consumer.");
        }

        startMqttPublisher(connectionSettings);
    }

    private void startMqttPublisher(final MqttConnectionSettings connectionSettings) {
        log.info("Starting MQTT publisher actor.");
        final Sink<MqttMessage, CompletionStage<Done>> mqttSink =
                MqttSink.create(connectionSettings.withClientId(connectionId() + "-publisher"), MqttQoS.atMostOnce());

        final Pair<Pair<ActorRef, UniqueKillSwitch>, CompletionStage<Done>> outbound =
                akka.stream.javadsl.Source.<MqttMessage>actorRef(100, OverflowStrategy.dropHead())
                        .map(this::countPublishedMqttMessage)
                        .viaMat(KillSwitches.single(), Keep.both())
                        .toMat(mqttSink, Keep.both())
                        .run(materializer);

        publisherKillSwitch = outbound.first().second();
        outbound.second().thenAccept(d -> log.info("Publisher ready..."));
        final ActorRef mqttPublisherSource = outbound.first().first();
        mqttPublisherActor = startChildActorConflictFree(MqttPublisherActor.ACTOR_NAME,
                MqttPublisherActor.props(mqttPublisherSource));
    }

    private void startMqttConsumers(final MqttConnectionSettings connectionSettings,
            final ActorRef messageMappingProcessorActor, final Source source) {
        consumerKillSwitch = KillSwitches.shared("consumerKillSwitch");

        final List<Pair<String, MqttQoS>> subscriptions = new ArrayList<>();
        source.getAddresses()
                .forEach(sourceAddress -> subscriptions.add(Pair.create(sourceAddress, MqttQoS.atMostOnce())));

        for (int i = 0; i < source.getConsumerCount(); i++) {

            log.debug("Starting {}. consumer actor for source <{}> on connection <{}>.", i, source.getIndex(),
                    connectionId());

            final String uniqueSuffix = getUniqueSourceSuffix(source.getIndex(), i);
            final String clientId = connectionId() + "-source-" + uniqueSuffix;
            final String actorName = MqttConsumerActor.ACTOR_NAME_PREFIX + uniqueSuffix;
            final MqttSourceSettings settings =
                    MqttSourceSettings.create(connectionSettings.withClientId(clientId))
                            .withSubscriptions(JavaConverters.asScalaBuffer(subscriptions).toSeq());

            final Props mqttConsumerActorProps =
                    MqttConsumerActor.props(messageMappingProcessorActor, source.getAuthorizationContext());
            final ActorRef mqttConsumerActor = startChildActorConflictFree(actorName, mqttConsumerActorProps);

            consumerByActorNameWithIndex.put(actorName, mqttConsumerActor);

            // TODO make configurable
            final Integer bufferSize = 8;
            final akka.stream.javadsl.Source<?, CompletionStage<Done>> mqttSource =
                    createMqttSource(settings, bufferSize, source);

            // TODO use RestartSource
//            final akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> restartingSource =
//                    wrapWithAsRestartSource(() -> mqttSource);
//                        .mapAsync(1, cm -> cm.messageArrivedComplete().thenApply(unused2 -> cm.message()))
//                        .take(input.size())

            // TODO use Sink.actorRefWithAck?
            //.runWith(Sink.actorRef(mqttConsumerActor, COMPLETE_MESSAGE), materializer);

            final Pair<CompletionStage<Done>, SharedKillSwitch> completions = mqttSource
                    .viaMat(consumerKillSwitch.flow(), Keep.both())
                    .map(this::countConsumedMqttMessage)
                    .toMat(Sink.actorRef(mqttConsumerActor, COMPLETE_MESSAGE), Keep.left())
                    .run(materializer);

            final CompletionStage<Done> subscriptionInitialized = completions.first();
            subscriptionInitialized.thenAccept(d -> log.info("Subscriptions {} initialized", subscriptions));

            log.info("Waiting for subscriptions....");
            subscriptionInitialized.toCompletableFuture().join();

            final SharedKillSwitch ks = completions.second();
            log.info("kill switch: {}", consumerKillSwitch);
            log.info("kill switch: {}", ks);
        }
    }

    private <T> T countConsumedMqttMessage(final T mqttMessage) {
        getSelf().tell(new CountConsumedMqttMessage(), ActorRef.noSender());
        return mqttMessage;
    }

    private <T> T countPublishedMqttMessage(final T mqttMessage) {
        getSelf().tell(new CountPublishedMqttMessage(), ActorRef.noSender());
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

        if (consumerKillSwitch != null) {
            log.info("Closing consumers.");
            consumerKillSwitch.shutdown();
            consumerKillSwitch = null;
        }

        if (publisherKillSwitch != null) {
            log.info("Closing publishers.");
            publisherKillSwitch.shutdown();
            publisherKillSwitch = null;
        }

        stopCommandConsumers();
        stopCommandProducers();
    }

    private void stopCommandProducers() {
        stopChildActor(mqttPublisherActor);
        mqttPublisherActor = null;
    }

    private void stopCommandConsumers() {
        consumerByActorNameWithIndex.forEach((actorNamePrefix, child) -> stopChildActor(child));
        consumerByActorNameWithIndex.clear();
    }

    @Override
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(mqttPublisherActor);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        self().tell((ClientConnected) () -> null, origin);
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

    private static akka.stream.javadsl.Source<?, CompletionStage<Done>> createMqttSource(
            final MqttSourceSettings settings,
            final int bufferSize,
            final Source source) {

        final MqttQoS qos = MqttValidator.getQoSFromValidConfig(source.getSpecificConfig());
        if (qos == MqttQoS.atLeastOnce()) {
            return MqttSource.atLeastOnce(settings, bufferSize);
        } else {
            return MqttSource.atMostOnce(settings, bufferSize);
        }
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
    private static final class CountPublishedMqttMessage {}

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
}
