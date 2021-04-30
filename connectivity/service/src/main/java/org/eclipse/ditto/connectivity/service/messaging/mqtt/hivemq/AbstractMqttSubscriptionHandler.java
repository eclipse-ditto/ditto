/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.datatypes.MqttQos;

import akka.actor.ActorRef;

/**
 * Handles subscriptions of MQTT connections.
 *
 * @param <S> MqttXSubscribe
 * @param <P> MqttXPublish
 * @param <R> MqttXSubAck
 */
abstract class AbstractMqttSubscriptionHandler<S, P, R> {

    private static final MqttQos DEFAULT_SOURCE_QOS = MqttQos.EXACTLY_ONCE;
    private final Connection connection;
    private final SubscribeAction<S, P, R> client;
    private final ThreadSafeDittoLoggingAdapter logger;

    private final Map<Source, ActorRef> consumerActors = new HashMap<>();
    private final Map<Source, S> mqtt3Subscribe;

    AbstractMqttSubscriptionHandler(final Connection connection, final SubscribeAction<S, P, R> client,
            final ThreadSafeDittoLoggingAdapter logger) {

        this.connection = connection;
        this.client = client;
        this.logger = logger;
        mqtt3Subscribe = prepareSubscriptions();
    }

    /**
     * Compute the SUBSCRIBE message from the source defining the consumer.
     *
     * @param source the connection source.
     * @return the SUBSCRIBE message.
     */
    abstract Optional<S> toMqttSubscribe(final Source source);

    /**
     * @return all source/consumer-actor pairs known to this subscription handler as a stream.
     */
    Stream<MqttConsumer> stream() {
        return consumerActors.entrySet().stream().map(entry -> MqttConsumer.of(entry.getKey(), entry.getValue()));
    }

    /**
     * Add a source/consumer pair.
     *
     * @param consumer the source/consumer pair.
     */
    void handleMqttConsumer(final MqttConsumer consumer) {
        consumerActors.put(consumer.getSource(), consumer.getConsumerActor());
    }

    /**
     * @return Whether consumer actors were started for all sources of the connection.
     */
    private boolean allConsumersReady() {
        return consumerActors.keySet().containsAll(connection.getSources());
    }

    /**
     * Remove all consumer actors added so far.
     *
     * @param clearedListener what to do on each removed consumer.
     */
    void clearConsumerActors(final Consumer<ActorRef> clearedListener) {
        consumerActors.values().forEach(clearedListener);
        consumerActors.clear();
    }

    /**
     * Send all SUBSCRIBE messages defined by the sources of this connection.
     *
     * @return Future list of all SUBACK messages.
     */
    CompletionStage<List<R>> subscribe() {
        final boolean allConsumersReady = allConsumersReady();
        if (allConsumersReady) {
            logger.info("Client connected and all consumers ready, subscribing now.");
            final List<CompletableFuture<R>> subAckFutures = mqtt3Subscribe.entrySet()
                    .stream()
                    .map(e -> {
                        final Source source = e.getKey();
                        final S theMqttSubscribe = e.getValue();
                        final ActorRef consumerActorRef = consumerActors.get(source);
                        return consumerActorRef == null
                                ? CompletableFuture.<R>failedFuture(new IllegalStateException("no consumer"))
                                : subscribe(source, theMqttSubscribe, consumerActorRef);
                    })
                    .collect(Collectors.toList());
            return CompletableFuture.allOf(subAckFutures.toArray(CompletableFuture[]::new))
                    .thenApply(unused -> subAckFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList())
                    );
        } else {
            final String message = "Consumers are not initialized, not subscribing.";
            logger.error(message);
            return CompletableFuture.failedFuture(new IllegalStateException(message));
        }
    }

    /**
     * Create a address/QoS pair.
     *
     * @param source source defining the QoS.
     * @param address the address.
     * @return the address/QoS pair.
     */
    Entry<String, MqttQos> asAddressQoSPair(final Source source, final String address) {
        return new SimpleImmutableEntry<>(address,
                source.getQos().map(MqttQos::fromCode).orElse(DEFAULT_SOURCE_QOS));
    }

    private CompletableFuture<R> subscribe(final Source source, final S mqtt3Subscribe,
            final ActorRef consumerActor) {
        // enable manual acknowledgement:
        // individual incoming message may carry requested-acks even if the source does not
        return client.subscribe(mqtt3Subscribe, msg -> consumerActor.tell(msg, ActorRef.noSender()), true)
                .whenComplete((mqtt3SubAck, throwable) -> {
                    if (throwable != null) {
                        // Handle failure to subscribe
                        logger.warning("Error subscribing to topics: <{}>: {}", source.getAddresses(),
                                throwable.getMessage());
                    } else {
                        // Handle successful subscription, e.g. logging or incrementing a metric
                        logger.info("Successfully subscribed to <{}>", source.getAddresses());
                    }
                });
    }

    private Map<Source, S> prepareSubscriptions() {
        return connection.getSources()
                .stream()
                .map(source -> toMqttSubscribe(source).map(sub -> new SimpleImmutableEntry<>(source, sub)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(fromEntries());
    }

    private static <K, V> Collector<Entry<K, V>, ?, Map<K, V>> fromEntries() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    /**
     * Wraps a {@link org.eclipse.ditto.connectivity.model.Source} and the associated consumer actor.
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

    /**
     * Encapsulate the "subscribe" method for MQTT3 and MQTT5 clients.
     *
     * @param <S> MqttXSubscribe
     * @param <P> MqttXPublish
     * @param <R> MqttXSubAck
     */
    @FunctionalInterface
    public interface SubscribeAction<S, P, R> {

        /**
         * Send a SUBSCRIBE message.
         *
         * @param subscribeMessage the SUBSCRIBE message.
         * @param callback what to do on each incoming PUBLISH message.
         * @param manualAcknowledgement whether manual acknowledgement is on.
         * @return future SUBACK.
         */
        CompletableFuture<R> subscribe(S subscribeMessage, Consumer<P> callback, boolean manualAcknowledgement);
    }
}
