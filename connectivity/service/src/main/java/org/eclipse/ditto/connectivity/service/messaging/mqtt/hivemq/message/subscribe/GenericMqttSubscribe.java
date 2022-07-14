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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;

/**
 * Generic representation of an MQTT Subscribe message of protocol versions 3 and 5.
 */
@Immutable
public final class GenericMqttSubscribe {

    private final LinkedHashSet<GenericMqttSubscription> genericMqttSubscriptions;

    private GenericMqttSubscribe(final Set<GenericMqttSubscription> genericMqttSubscriptions) {
        this.genericMqttSubscriptions = new LinkedHashSet<>(genericMqttSubscriptions);
    }

    /**
     * Returns an instance of {@code GenericMqttSubscribe} for the specified MQTT subscriptions.
     *
     * @param genericMqttSubscriptions the subscriptions of the returned Subscribe message. The set must contain at
     * least one subscription.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code genericMqttSubscriptions} is empty.
     */
    public static GenericMqttSubscribe of(final Set<GenericMqttSubscription> genericMqttSubscriptions) {
        return new GenericMqttSubscribe(ConditionChecker.argumentNotEmpty(genericMqttSubscriptions,
                "genericMqttSubscriptions"));
    }

    /**
     * Returns a stream of the subscriptions of this Subscribe message.
     *
     * @return a stream of the subscriptions of this Subscribe message.
     */
    public Stream<GenericMqttSubscription> genericMqttSubscriptions() {
        return genericMqttSubscriptions.stream();
    }

    /**
     * Returns this Subscribe message as {@link Mqtt3Subscribe}.
     *
     * @return this Subscribe message as {@link Mqtt3Subscribe}.
     */
    public Mqtt3Subscribe getAsMqtt3Subscribe() {
        return Mqtt3Subscribe.builder()
                .addSubscriptions(genericMqttSubscriptions().map(GenericMqttSubscription::getAsMqtt3Subscription))
                .build();
    }

    /**
     * Returns this Subscribe message as {@link Mqtt5Subscribe}.
     *
     * @return this Subscribe message as {@link Mqtt5Subscribe}.
     */
    public Mqtt5Subscribe getAsMqtt5Subscribe() {
        return Mqtt5Subscribe.builder()
                .addSubscriptions(genericMqttSubscriptions().map(GenericMqttSubscription::getAsMqtt5Subscription))
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (GenericMqttSubscribe) o;
        return Objects.equals(genericMqttSubscriptions, that.genericMqttSubscriptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genericMqttSubscriptions);
    }

}
