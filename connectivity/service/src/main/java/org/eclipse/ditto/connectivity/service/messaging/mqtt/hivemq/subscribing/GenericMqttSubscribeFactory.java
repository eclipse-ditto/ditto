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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

/**
 * This factory creates an optional {@link GenericMqttSubscribe} for each address of a Connection {@link Source}.
 * The Optional is present if the Source has at least one address, else the Optional is empty.
 */
@Immutable
final class GenericMqttSubscribeFactory {

    private GenericMqttSubscribeFactory() {
        throw new AssertionError();
    }

    /**
     * Returns the optional {@code GenericMqttSubscribe} for the specified {@code Source} argument.
     *
     * @param connectionSource the Connection Source to get the generic MQTT Subscribe message for.
     * @return an Optional containing the generic MQTT Subscribe message or an empty optional if
     * {@code connectionSource} did not contain addresses.
     * @throws NullPointerException if {@code connectionSource} is {@code null}.
     * @throws InvalidMqttTopicFilterStringException if any address of {@code connectionSource} is not a valid
     * {@link MqttTopicFilter}.
     */
    static Optional<GenericMqttSubscribe> getGenericSourceSubscribeMessage(final Source connectionSource)
            throws InvalidMqttTopicFilterStringException {

        ConditionChecker.checkNotNull(connectionSource, "connectionSource");
        final Optional<GenericMqttSubscribe> result;
        final var connectionSourceAddresses = connectionSource.getAddresses();
        if (connectionSourceAddresses.isEmpty()) {
            result = Optional.empty();
        } else {
            result = Optional.of(GenericMqttSubscribe.of(tryToGetGenericMqttSubscriptions(connectionSourceAddresses,
                    getMqttQos(connectionSource))));
        }
        return result;
    }

    private static MqttQos getMqttQos(final Source source) {
        return source.getQos().map(MqttQos::fromCode).orElse(MqttQos.EXACTLY_ONCE);
    }

    private static Set<GenericMqttSubscription> tryToGetGenericMqttSubscriptions(
            final Collection<String> sourceAddresses,
            final MqttQos mqttQos
    ) throws InvalidMqttTopicFilterStringException {

        final var result = new LinkedHashSet<GenericMqttSubscription>(sourceAddresses.size());
        for (final var sourceAddress : sourceAddresses) {
            result.add(GenericMqttSubscription.newInstance(tryToGetMqttTopicFilter(sourceAddress), mqttQos));
        }
        return result;
    }

    private static MqttTopicFilter tryToGetMqttTopicFilter(final String sourceAddress)
            throws InvalidMqttTopicFilterStringException {

        try {
            return MqttTopicFilter.of(sourceAddress);
        } catch (final IllegalArgumentException e) {
            throw new InvalidMqttTopicFilterStringException(
                    MessageFormat.format("Failed to instantiate {0} for <{1}>: {2}",
                            MqttTopicFilter.class.getSimpleName(),
                            sourceAddress,
                            e.getMessage()),
                    e
            );
        }
    }

}
