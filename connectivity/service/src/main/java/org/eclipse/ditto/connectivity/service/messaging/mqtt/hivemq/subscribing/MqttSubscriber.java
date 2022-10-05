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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttSubscribingClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * This utility class facilitates subscribing for topics derived from the addresses of connection
 * {@link org.eclipse.ditto.connectivity.model.Source}s at the MQTT broker.
 */
public final class MqttSubscriber {

    private final GenericMqttSubscribingClient subscribingClient;

    private MqttSubscriber(final GenericMqttSubscribingClient subscribingClient) {
        this.subscribingClient = subscribingClient;
    }

    /**
     * Returns a new instance of {@code MqttSubscriber} for the specified {@code GenericMqttSubscribingClient}.
     *
     * @param genericMqttSubscribingClient the client to be used for subscribing to topics at the broker and for
     * consuming incoming Publish message for the subscribed topics.
     * @return the instance.
     * @throws NullPointerException if {@code genericMqttSubscribingClient} is {@code null}.
     */
    public static MqttSubscriber newInstance(final GenericMqttSubscribingClient genericMqttSubscribingClient) {
        return new MqttSubscriber(checkNotNull(genericMqttSubscribingClient, "genericMqttSubscribingClient"));
    }

    /**
     * Subscribes the specified {@code GenericMqttSubscribingClient} for the addresses of the specified connection
     * sources.
     * For each connection source an MQTT Subscribe message is created and sent to the broker by the client.
     * The MQTT Subscribe message contains an MQTT Subscription for each address of the connection source where the
     * address is regarded as MQTT filter topic and the MQTT QoS is taken from the connection source as provided.
     * The returned Akka stream contains the results of the client's subscribing for each connection source.
     * If a connection source does not provide any addresses then no Subscribe message is created for that source –
     * thus, there is no {@code SubscribeResult} in the returned Akka stream for that connection source.
     * A connection source address might not be a valid MQTT filter topic.
     * In this case the SubscribeResult for the associated connection source is a failure.
     * <p>
     * A {@code SubscribeResult} is only then successful if all subscriptions to its connection source addresses
     * succeeded.
     *
     * @param connectionSources the connection sources to subscribe for.
     * @return an Akka stream containing the client subscribing results with their associated connection sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public Source<SubscribeResult, NotUsed> subscribeForConnectionSources(
            final List<org.eclipse.ditto.connectivity.model.Source> connectionSources
    ) {
        checkNotNull(connectionSources, "connectionSources");

        // Use Pairs to carry along associated connection Source.
        return Source.fromIterator(connectionSources::iterator)
                .map(MqttSubscriber::tryToGetGenericMqttSubscribe)
                .map(optionalTryPair -> Pair.create(
                        optionalTryPair.first(),
                        optionalTryPair.second()
                                .map(optionalSubscribeMsg -> Source.fromJavaStream(optionalSubscribeMsg::stream))
                                .map(subscribeMsgSource -> subscribeMsgSource.flatMapConcat(
                                        subscribeMsg -> subscribe(subscribeMsg, optionalTryPair.first()))
                                )
                ))
                .flatMapConcat(pair -> pair.second()
                        .fold(
                                error -> getSubscribeFailureSource(pair.first(), error),
                                sourceSubscribeResultSource -> sourceSubscribeResultSource
                        ));
    }

    private static Pair<org.eclipse.ditto.connectivity.model.Source, Try<Optional<GenericMqttSubscribe>>> tryToGetGenericMqttSubscribe(
            final org.eclipse.ditto.connectivity.model.Source connectionSource
    ) {
        try {
            return Pair.create(
                    connectionSource,
                    new Success<>(GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(connectionSource))
            );
        } catch (final InvalidMqttTopicFilterStringException e) {
            return Pair.create(connectionSource, new Failure<>(e));
        }
    }

    private Source<SubscribeResult, NotUsed> subscribe(final GenericMqttSubscribe genericMqttSubscribe,
            final org.eclipse.ditto.connectivity.model.Source connectionSource) {

        return Source.fromPublisher(subscribingClient.subscribe(genericMqttSubscribe) // <- there
                .map(unused -> consumeIncomingPublishesForSubscribedTopics(connectionSource))
                .onErrorReturn(error -> getSubscribeFailureResult(connectionSource, error))
                .toFlowable());
    }

    private SubscribeResult consumeIncomingPublishesForSubscribedTopics(
            final org.eclipse.ditto.connectivity.model.Source connectionSource
    ) {
        final List<MqttTopicFilter> topicFilters =
                connectionSource.getAddresses().stream().map(MqttTopicFilter::of).toList();
        return SubscribeSuccess.newInstance(connectionSource,
                Source.fromPublisher(subscribingClient.consumeSubscribedPublishesWithManualAcknowledgement()
                        .filter(publish -> messageHasRightTopicPath(publish, topicFilters))));
    }

    /**
     * Filters out messages which don't match any of the given topic filters. This is done because the HiveMQ API makes
     * it hard to consume only messages which match specific topics in the first place.
     *
     * @param genericMqttPublish a consumed MQTT message.
     * @param topicFilters the topic filters applied to consumed messages.
     * @return whether the message matches any of the given topic filters.
     */
    private boolean messageHasRightTopicPath(final GenericMqttPublish genericMqttPublish,
            final List<MqttTopicFilter> topicFilters) {
        return topicFilters.stream().anyMatch(filter -> filter.matches(genericMqttPublish.getTopic()));
    }

    private static SubscribeResult getSubscribeFailureResult(
            final org.eclipse.ditto.connectivity.model.Source connectionSource,
            final Throwable failure
    ) {
        final SubscribeFailure result;
        if (failure instanceof MqttSubscribeException mqttSubscribeException) {
            result = SubscribeFailure.newInstance(connectionSource, mqttSubscribeException);
        } else {
            result = SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException(failure));
        }
        return result;
    }

    private static Source<SubscribeResult, NotUsed> getSubscribeFailureSource(
            final org.eclipse.ditto.connectivity.model.Source connectionSource,
            final Throwable error
    ) {
        return Source.single(
                SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException(
                        MessageFormat.format("Failed to instantiate {0}: {1}",
                                GenericMqttSubscribe.class.getSimpleName(),
                                error.getMessage()),
                        error
                )));
    }

}