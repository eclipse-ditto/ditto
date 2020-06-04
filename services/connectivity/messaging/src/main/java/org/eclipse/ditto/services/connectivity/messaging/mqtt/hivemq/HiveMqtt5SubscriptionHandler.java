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
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Handles subscriptions of MQTT 5 connections.
 *
 * @since 1.1.0
 */
final class HiveMqtt5SubscriptionHandler {

    private static final MqttQos DEFAULT_QOS = MqttQos.AT_MOST_ONCE;
    private final Connection connection;
    private final Mqtt5Client client;
    private final CompletableFuture<Status.Status> subscriptionsDone;
    private final DiagnosticLoggingAdapter log;

    private final Map<Source, ActorRef> consumerActors = new HashMap<>();
    private final Map<Source, Mqtt5Subscribe> mqtt5Subscribe;

    private boolean isConnected = false;

    HiveMqtt5SubscriptionHandler(final Connection connection, final Mqtt5Client client,
            final DiagnosticLoggingAdapter log) {
        this.connection = connection;
        this.client = client;
        this.subscriptionsDone = new CompletableFuture<>();
        this.log = log;
        mqtt5Subscribe = prepareSubscriptions();
    }

    void handleConnected() {
        isConnected = true;
        subscribeIfReady();
    }

    void handleDisconnected() {
        isConnected = false;
    }

    void handleMqttConsumer(final Mqtt5Consumer consumer) {
        consumerActors.put(consumer.getSource(), consumer.getConsumerActor());
        subscribeIfReady();
    }

    Optional<ActorRef> findAnyConsumerActor() {
        return consumerActors.values().stream().findAny();
    }

    CompletionStage<Status.Status> getCompletionStage() {
        return subscriptionsDone;
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
                    .allOf(mqtt5Subscribe.entrySet().stream()
                            .map(e -> {
                                final Source source = e.getKey();
                                final Mqtt5Subscribe theMqtt5Subscribe = e.getValue();
                                final ActorRef consumerActorRef = consumerActors.get(source);
                                if (consumerActorRef == null) {
                                    return failedFuture(new IllegalStateException("no consumer"));
                                } else {
                                    return subscribe(source, theMqtt5Subscribe, consumerActorRef);
                                }
                            })
                            .toArray(CompletableFuture[]::new))
                    .whenComplete((result, t) -> {
                        if (t == null) {
                            log.debug("All subscriptions created successfully.");
                            subscriptionsDone.complete(new Status.Success("successfully subscribed"));
                        } else {
                            log.info("Subscribe failed due to: {}", t.getMessage());
                            subscriptionsDone.completeExceptionally(t);
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

    private CompletableFuture<Mqtt5SubAck> subscribe(final Source source, final Mqtt5Subscribe mqtt5Subscribe,
            final ActorRef consumerActor) {
        return client.toAsync()
                .subscribe(mqtt5Subscribe, msg -> consumerActor.tell(msg, ActorRef.noSender()), true)
                .whenComplete((mqtt5SubAck, throwable) -> {
                    if (throwable != null) {
                        // Handle failure to subscribe
                        log.warning("Error subscribing to topics: <{}>: {}", source.getAddresses(),
                                throwable.getMessage());
                    } else {
                        // Handle successful subscription, e.g. logging or incrementing a metric
                        log.info("Successfully subscribed to <{}>", source.getAddresses());

                    }
                });
    }

    private Map<Source, Mqtt5Subscribe> prepareSubscriptions() {
        return connection.getSources()
                .stream()
                .map(source -> toMqtt5Subscribe(source).map(sub -> new SimpleImmutableEntry<>(source, sub)))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(fromEntries());
    }

    private Optional<Mqtt5Subscribe> toMqtt5Subscribe(final Source source) {
        final Mqtt5SubscribeBuilder.Start subscribeBuilder = Mqtt5Subscribe.builder();
        return source.getAddresses().stream().map(address -> asEntry(source, address))
                .map(e -> Mqtt5Subscription.builder()
                        .topicFilter(e.getKey())
                        .qos(e.getValue())
                        .build())
                .map(subscribeBuilder::addSubscription)
                .reduce((b1, b2) -> b2)
                .map(Mqtt5SubscribeBuilder.Complete::build);
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
    static class Mqtt5Consumer {

        private final Source source;
        private final ActorRef consumerActor;

        private Mqtt5Consumer(final Source source, final ActorRef consumerActor) {
            this.consumerActor = consumerActor;
            this.source = source;
        }

        static Mqtt5Consumer of(final Source source, final ActorRef consumerActor) {
            return new Mqtt5Consumer(source, consumerActor);
        }

        private Source getSource() {
            return source;
        }

        private ActorRef getConsumerActor() {
            return consumerActor;
        }
    }
}
