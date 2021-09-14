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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link Amqp091Config}.
 */
@Immutable
final class DefaultAmqp091Config implements Amqp091Config {

    private static final String CONFIG_PATH = "amqp091";

    private final Duration publisherPendingAckTTL;

    private DefaultAmqp091Config(final ScopedConfig config) {
        publisherPendingAckTTL = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.PUBLISHER_PENDING_ACK_TTL);
    }

    /**
     * Returns an instance of {@code DefaultAmqp10Config} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAmqp091Config of(final Config config) {
        return new DefaultAmqp091Config(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o instanceof DefaultAmqp091Config) {
            final DefaultAmqp091Config that = (DefaultAmqp091Config) o;
            return Objects.equals(publisherPendingAckTTL, that.publisherPendingAckTTL);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(publisherPendingAckTTL);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "publisherPendingAckTTL=" + publisherPendingAckTTL +
                "]";
    }

    @Override
    public Duration getPublisherPendingAckTTL() {
        return publisherPendingAckTTL;
    }
}
