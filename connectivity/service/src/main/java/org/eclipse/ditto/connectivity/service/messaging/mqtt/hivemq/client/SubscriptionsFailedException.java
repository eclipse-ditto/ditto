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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Base implementation of an {@link MqttSubscribeException} which has failed {@link SubscriptionStatus}es.
 */
public abstract sealed class SubscriptionsFailedException extends MqttSubscribeException
        permits AllSubscriptionsFailedException, SomeSubscriptionsFailedException {

    private final transient List<SubscriptionStatus> failedSubscriptionStatuses;

    protected SubscriptionsFailedException(final List<SubscriptionStatus> failedSubscriptionStatuses,
            @Nullable final Throwable cause) {

        super(cause);
        this.failedSubscriptionStatuses = List.copyOf(
                ConditionChecker.argumentNotEmpty(failedSubscriptionStatuses, "failedSubscriptionStatuses")
        );
    }

    /**
     * Returns an unmodifiable unsorted List containing the status of each failed subscription.
     *
     * @return the failed subscription statuses.
     */
    public Stream<SubscriptionStatus> failedSubscriptionStatuses() {
        return failedSubscriptionStatuses.stream();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SubscriptionsFailedException) o;
        return Objects.equals(failedSubscriptionStatuses, that.failedSubscriptionStatuses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedSubscriptionStatuses);
    }

}
