/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements the config for the handling of event journal entries.
 */
@Immutable
public final class DefaultEventConfig implements EventConfig {

    private static final String CONFIG_PATH = "event";

    private final List<String> historicalHeadersToPersist;

    private DefaultEventConfig(final ScopedConfig config) {
        historicalHeadersToPersist = Collections.unmodifiableList(new ArrayList<>(
                config.getStringList(EventConfigValue.HISTORICAL_HEADERS_TO_PERSIST.getConfigPath())
        ));
    }

    /**
     * Returns an instance of the default event journal config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the event journal config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultEventConfig of(final Config config) {
        return new DefaultEventConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, EventConfigValue.values()));
    }

    @Override
    public List<String> getHistoricalHeadersToPersist() {
        return historicalHeadersToPersist;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultEventConfig that = (DefaultEventConfig) o;
        return Objects.equals(historicalHeadersToPersist, that.historicalHeadersToPersist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(historicalHeadersToPersist);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "historicalHeadersToPersist=" + historicalHeadersToPersist +
                "]";
    }

}
