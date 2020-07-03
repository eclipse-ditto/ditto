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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
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
    private final Mqtt3AsyncClient client;
    private final DiagnosticLoggingAdapter log;

    private final Map<Source, ActorRef> consumerActors = new HashMap<>();
    private final Map<Source, Mqtt3Subscribe> mqtt3Subscribe;

    HiveMqtt3SubscriptionHandler(final Connection connection, final Mqtt3AsyncClient client,
            final DiagnosticLoggingAdapter log) {
        this.connection = connection;
        this.client = client;
        this.log = log;
        mqtt3Subscribe = prepareSubscriptions();
    }

    void handleMqttConsumer(final MqttConsumer consumer) {
        consumerActors.put(consumer.getSource(), consumer.getConsumerActor());
    }

    private boolean allConsumersReady() {
        return consumerActors.keySet().containsAll(connection.getSources());
    }

    void clearConsumerActors(final Consumer<ActorRef> clearedListener) {
        consumerActors.values().forEach(clearedListener);
        consumerActors.clear();
    }

    CompletionStage<List<Mqtt3SubAck>> subscribe() {
        final boolean allConsumersReady = allConsumersReady();
        if (allConsumersReady) {
            log.info("Client connected and all consumers ready, subscribing now.");
            final List<CompletableFuture<Mqtt3SubAck>> subAckFutures = mqtt3Subscribe.entrySet()
                    .stream()
                    .map(e -> {
                        final Source source = e.getKey();
                        final Mqtt3Subscribe theMqtt3Subscribe = e.getValue();
                        final ActorRef consumerActorRef = consumerActors.get(source);
                        return consumerActorRef == null
                                ? CompletableFuture.<Mqtt3SubAck>failedFuture(new IllegalStateException("no consumer"))
                                : subscribe(source, theMqtt3Subscribe, consumerActorRef);
                    })
                    .collect(Collectors.toList());
            return CompletableFuture.allOf(subAckFutures.toArray(CompletableFuture[]::new))
                    .thenApply(_void -> subAckFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList())
                    );
        } else {
            final String message = "Consumers are not initialized, not subscribing.";
            log.error(message);
            return CompletableFuture.failedFuture(new IllegalStateException(message));
        }
    }

    private CompletableFuture<Mqtt3SubAck> subscribe(final Source source, final Mqtt3Subscribe mqtt3Subscribe,
            final ActorRef consumerActor) {
        // enable manual acknowledgement:
        // individual incoming message may carry requested-acks even if the source does not
        return client.subscribe(mqtt3Subscribe, msg -> consumerActor.tell(msg, ActorRef.noSender()), true)
                .whenComplete((mqtt3SubAck, throwable) -> {
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
