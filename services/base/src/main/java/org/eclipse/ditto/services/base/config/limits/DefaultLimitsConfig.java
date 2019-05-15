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
package org.eclipse.ditto.services.base.config.limits;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link LimitsConfig}.
 */
@Immutable
public final class DefaultLimitsConfig implements LimitsConfig, Serializable, WithConfigPath {

    /**
     * Path of the limits configuration settings.
     */
    public static final String CONFIG_PATH = "limits";

    private static final long serialVersionUID = -2234806942804455395L;

    private final long thingsMaxSize;
    private final long policiesMaxSize;
    private final long messagesMaxSize;
    private final int thingsSearchDefaultPageSize;
    private final int thingsSearchMaxPageSize;

    private DefaultLimitsConfig(final ScopedConfig config) {
        thingsMaxSize = config.getBytes(LimitsConfigValue.THINGS_MAX_SIZE.getConfigPath());
        policiesMaxSize = config.getBytes(LimitsConfigValue.POLICIES_MAX_SIZE.getConfigPath());
        messagesMaxSize = config.getBytes(LimitsConfigValue.MESSAGES_MAX_SIZE.getConfigPath());
        thingsSearchDefaultPageSize = config.getInt(LimitsConfigValue.THINGS_SEARCH_DEFAULT_PAGE_SIZE.getConfigPath());
        thingsSearchMaxPageSize = config.getInt(LimitsConfigValue.THINGS_SEARCH_MAX_PAGE_SIZE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultLimitsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the limits config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultLimitsConfig of(final Config config) {
        return new DefaultLimitsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, LimitsConfigValue.values()));
    }

    @Override
    public long getThingsMaxSize() {
        return thingsMaxSize;
    }

    @Override
    public long getPoliciesMaxSize() {
        return policiesMaxSize;
    }

    @Override
    public long getMessagesMaxSize() {
        return messagesMaxSize;
    }

    @Override
    public int getThingsSearchDefaultPageSize() {
        return thingsSearchDefaultPageSize;
    }

    @Override
    public int getThingsSearchMaxPageSize() {
        return thingsSearchMaxPageSize;
    }

    /**
     * @return always {@value #CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultLimitsConfig that = (DefaultLimitsConfig) o;
        return thingsMaxSize == that.thingsMaxSize &&
                policiesMaxSize == that.policiesMaxSize &&
                messagesMaxSize == that.messagesMaxSize &&
                thingsSearchDefaultPageSize == that.thingsSearchDefaultPageSize &&
                thingsSearchMaxPageSize == that.thingsSearchMaxPageSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingsMaxSize, policiesMaxSize, messagesMaxSize, thingsSearchDefaultPageSize,
                thingsSearchMaxPageSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingsMaxSize=" + thingsMaxSize +
                ", policiesMaxSize=" + policiesMaxSize +
                ", messagesMaxSize=" + messagesMaxSize +
                ", thingsSearchDefaultPageSize=" + thingsSearchDefaultPageSize +
                ", thingsSearchMaxPageSize=" + thingsSearchMaxPageSize +
                "]";
    }

}
