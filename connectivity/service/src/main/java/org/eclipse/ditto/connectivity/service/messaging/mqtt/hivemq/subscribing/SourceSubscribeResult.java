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

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;

import akka.NotUsed;

/**
 * This implementation of {@code SubscribeResult} is aware of its associated connection {@link Source}.
 */
public final class SourceSubscribeResult implements SubscribeResult {

    private final Source connectionSource;
    private final SubscribeResult subscribeResult;

    private SourceSubscribeResult(final Source connectionSource, final SubscribeResult subscribeResult) {
        this.connectionSource = ConditionChecker.checkNotNull(connectionSource, "connectionSource");
        this.subscribeResult = ConditionChecker.checkNotNull(subscribeResult, "subscribeResult");
    }

    /**
     * Returns a new instance of {@code SourceSubscribeResult} for the specified arguments.
     *
     * @param connectionSource the connection source which is associated with {@code subscribeResult}.
     * @param subscribeResult another {@code SubscribeResult} to wrap and delegate all appropriate method calls to.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SourceSubscribeResult newInstance(final Source connectionSource,
            final SubscribeResult subscribeResult) {

        return new SourceSubscribeResult(connectionSource, subscribeResult);
    }

    /**
     * Returns the connection source which is associated with this subscribe result.
     *
     * @return the associated connection source.
     */
    public Source getConnectionSource() {
        return connectionSource;
    }

    @Override
    public boolean isSuccess() {
        return subscribeResult.isSuccess();
    }

    @Override
    public akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> getMqttPublishSourceOrThrow() {
        return subscribeResult.getMqttPublishSourceOrThrow();
    }

    @Override
    public MqttSubscribeException getErrorOrThrow() {
        return subscribeResult.getErrorOrThrow();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SourceSubscribeResult) o;
        return Objects.equals(connectionSource, that.connectionSource) &&
                Objects.equals(subscribeResult, that.subscribeResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionSource, subscribeResult);
    }

}
