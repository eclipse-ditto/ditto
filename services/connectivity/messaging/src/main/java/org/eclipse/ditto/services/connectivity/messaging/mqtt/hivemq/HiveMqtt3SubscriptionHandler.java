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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Handles subscriptions of MQTT connections.
 */
final class HiveMqtt3SubscriptionHandler {

    private static final MqttQos DEFAULT_QOS = MqttQos.AT_MOST_ONCE;
    private final Connection connection;
    private final Mqtt3Client client;
    private final Consumer<ConnectionFailure> failureHandler;
    private final Runnable subscriptionsDone;
    private final DiagnosticLoggingAdapter log;

    private final Map<Source, ActorRef> consumerActors = new HashMap<>();
    private final Map<Source, Mqtt3Subscribe> mqtt3Subscribe;

    private boolean isConnected = false;

    HiveMqtt3SubscriptionHandler(final Connection connection, final Mqtt3Client client,
            final Consumer<ConnectionFailure> failureHandler, final Runnable subscriptionsDone,
            final DiagnosticLoggingAdapter log) {
        this.connection = connection;
        this.client = client;
        this.failureHandler = failureHandler;
        this.subscriptionsDone = subscriptionsDone;
        this.log = log;
        mqtt3Subscribe = prepareSubscriptions();
    }

    void handleConnected() {
        isConnected = true;
        subscribeIfReady();
    }

    void handleDisconnected() {
        isConnected = false;
    }

    void handleMqttConsumer(final MqttConsumer consumer) {
        consumerActors.put(consumer.getSource(), consumer.getConsumerActor());
        subscribeIfReady();
    }

    private boolean allConsumersReady() {
        return consumerActors.keySet().containsAll(connection.getSources());
    }

    void clearConsumerActors(final Consumer<ActorRef> clearedListener) {
        consumerActors.values().forEach(clearedListener);
        consumerActors.clear();
    }

    private void subscribeIfReady() {
        if (!isConnected) {
            log.debug("Not connected, not subscribing.");
        } else if (!allConsumersReady()) {
            log.debug("Consumers are not initialized, not subscribing.");
        } else {
            log.info("Client connected and all consumers ready, subscribing now.");
            CompletableFuture
                    .allOf(mqtt3Subscribe.entrySet().stream()
                            .map(e -> {
                                final Source source = e.getKey();
                                final Mqtt3Subscribe mqtt3Subscribe = e.getValue();
                                final ActorRef consumerActorRef = consumerActors.get(source);
                                if (consumerActorRef == null) {
                                    failureHandler.accept(noConsumerActorFound(source));
                                    return failedFuture(new IllegalStateException("no consumer"));
                                } else {
                                    return subscribe(source, mqtt3Subscribe, consumerActorRef);
                                }
                            })
                            .toArray(CompletableFuture[]::new))
                    .whenComplete((result, t) -> {
                        if (t == null) {
                            log.debug("All subscriptions created successfully.");
                            subscriptionsDone.run();
                        } else {
                            log.info("Subscribe failed.");
                        }
                    });
        }
    }

    @Nonnull
    private static CompletableFuture<?> failedFuture(final Throwable throwable) {
        final CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(throwable);
        return failedFuture;
    }

    @Nonnull
    private ImmutableConnectionFailure noConsumerActorFound(final Source source) {
        return new ImmutableConnectionFailure(null, null,
                "No consumer actor found for source: " + source);
    }

    private CompletableFuture<Mqtt3SubAck> subscribe(final Source source, final Mqtt3Subscribe mqtt3Subscribe,
            final ActorRef consumerActor) {
        return client.toAsync()
                .subscribe(mqtt3Subscribe, msg -> consumerActor.tell(msg, ActorRef.noSender()))
                .whenComplete((mqtt3SubAck, throwable) -> {
                    if (throwable != null) {
                        // Handle failure to subscribe
                        log.warning("Error subscribing to topics: <{}>: {}", source.getAddresses(),
                                throwable.getMessage());
                        failureHandler.accept(new ImmutableConnectionFailure(null, throwable,
                                "Subscribe failed for source: " + source));
                    } else {
                        // Handle successful subscription, e.g. logging or incrementing a metric
                        log.info("Successfully subscribed to <{}>", source.getAddresses());

                    }
                });
    }

    private Map<Source, Mqtt3Subscribe> prepareSubscriptions() {
        return connection.getSources()
                .stream()
                .map(source -> toMqtt3Subscribe(source).map(sub -> new SimpleImmutableEntry<>(source, sub)))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(fromEntries());
    }

    private Optional<Mqtt3Subscribe> toMqtt3Subscribe(final Source source) {
        final Mqtt3SubscribeBuilder.Start subscribeBuilder = Mqtt3Subscribe.builder();
        return source.getAddresses().stream().map(address -> asEntry(source, address))
                .map(e -> Mqtt3Subscription.builder()
                        .topicFilter(e.getKey())
                        .qos(e.getValue())
                        .build())
                .map(subscribeBuilder::addSubscription)
                .reduce((b1, b2) -> b2)
                .map(Mqtt3SubscribeBuilder.Complete::build);
    }

    private Entry<String, MqttQos> asEntry(final Source source, final String address) {
        return new SimpleImmutableEntry<>(address,
                source.getQos().map(MqttQos::fromCode).orElse(DEFAULT_QOS));
    }

    private static <K, V> Collector<Entry<K, V>, ?, Map<K, V>> fromEntries() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    /**
     * Wraps a {@link Source} and the associated consumer actor.
     */
    static class MqttConsumer {

        private final Source source;
        private final ActorRef consumerActor;

        private MqttConsumer(final Source source, final ActorRef consumerActor) {
            this.consumerActor = consumerActor;
            this.source = source;
        }

        static MqttConsumer of(final Source source, final ActorRef consumerActor) {
            return new MqttConsumer(source, consumerActor);
        }

        private Source getSource() {
            return source;
        }

        private ActorRef getConsumerActor() {
            return consumerActor;
        }
    }
}
