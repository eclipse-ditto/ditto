/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

/**
 * Dummy/Noop subscriptions handler of MQTT connections which don't subscribe to any connection sources.
 */
final class DummySubscriptionHandler<S, P, R> extends AbstractMqttSubscriptionHandler<S, P, R> {

    DummySubscriptionHandler(final Connection connection, final ThreadSafeDittoLoggingAdapter logger) {
        // this DummySubscriptionHandler does not actually subscribe, but always return a null in the client future.
        super(connection,
                (subscribeMessage, callback, manualAcknowledgement) -> CompletableFuture.completedFuture(null), logger);
    }

    @Override
    Optional<S> toMqttSubscribe(final Source source) {
        return Optional.empty();
    }

}
