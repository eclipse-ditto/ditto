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

import javax.annotation.concurrent.Immutable;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * This utility class facilitates subscribing for topics derived from the addresses of connection
 * {@link org.eclipse.ditto.connectivity.model.Source}s at the MQTT broker.
 */
@Immutable
public final class MqttSubscriber {

    private MqttSubscriber() {
        throw new AssertionError();
    }

    /**
     * Subscribes the specified {@code GenericMqttSubscribingClient} for the addresses of the specified connection
     * sources.
     * For each connection source an MQTT Subscribe message is created and sent to the broker by the client.
     * The MQTT Subscribe message contains an MQTT Subscription for each address of the connection source where the
     * address is regarded as MQTT filter topic and the MQTT QoS is taken from the connection source as provided.
     * The returned Akka stream contains the results of the client's subscribing in the same order as the specified
     * connection sources.
     * If a connection source does not provide any addresses then no Subscribe message is created for that source –
     * thus, there is no {@code SubscribeResult} in the returned Akka stream for that connection source.
     * A connection source address might not be a valid MQTT filter topic.
     * In this case the SubscribeResult for the associated connection source is a failure.
     *
     * @param connectionSources the connection sources to subscribe for.
     * @param genericMqttSubscribingClient the client to send the MQTT Subscribe message to the broker.
     * @return an Akka stream containing the results of the client's subscribing in the same order as
     * {@code connectionSources}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Source<SubscribeResult, NotUsed> subscribeForConnectionSources(
            final List<org.eclipse.ditto.connectivity.model.Source> connectionSources,
            final GenericMqttSubscribingClient genericMqttSubscribingClient
    ) {
        checkNotNull(connectionSources, "connectionSources");
        checkNotNull(genericMqttSubscribingClient, "genericMqttSubscribingClient");

        return Source.from(connectionSources)
                .map(MqttSubscriber::tryToGetGenericMqttSubscribe)
                .map(optionalTry -> optionalTry.map(optional -> optional.map(Source::single).orElseGet(Source::empty)))
                .flatMapConcat(genericMqttSubscribesTry -> genericMqttSubscribesTry.fold(
                        MqttSubscriber::getSubscribeFailureSource,
                        subscribes -> subscribes.flatMapConcat(genericMqttSubscribingClient::subscribe)
                ));
    }

    private static Try<Optional<GenericMqttSubscribe>> tryToGetGenericMqttSubscribe(
            final org.eclipse.ditto.connectivity.model.Source connectionSource
    ) {
        try {
            return new Success<>(GenericMqttSubscribeFactory.getGenericSourceSubscribeMessage(connectionSource));
        } catch (final InvalidMqttTopicFilterStringException e) {
            return new Failure<>(e);
        }
    }

    private static Source<SubscribeResult, NotUsed> getSubscribeFailureSource(final Throwable error) {
        return Source.single(SubscribeFailure.newInstance(new MqttSubscribeException(
                MessageFormat.format("Failed to instantiate {0}: {1}",
                        GenericMqttSubscribe.class.getSimpleName(),
                        error.getMessage()),
                error
        )));
    }

}