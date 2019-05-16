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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link KafkaConfig}.
 */
@Immutable
public final class DefaultKafkaConfig implements KafkaConfig, Serializable {

    private static final String CONFIG_PATH = "kafka";

    private static final long serialVersionUID = 728630119797660446L;

    private final Config internalProducerConfig;

    private DefaultKafkaConfig(final ScopedConfig kafkaScopedConfig) {
        internalProducerConfig = kafkaScopedConfig.getConfig("producer.internal");
    }

    /**
     * Returns an instance of {@code DefaultKafkaConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultKafkaConfig of(final Config config) {
        return new DefaultKafkaConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    @Override
    public Config getInternalProducerConfig() {
        return internalProducerConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultKafkaConfig that = (DefaultKafkaConfig) o;
        return Objects.equals(internalProducerConfig, that.internalProducerConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internalProducerConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "internalProducerConfig=" + internalProducerConfig +
                "]";
    }

}
