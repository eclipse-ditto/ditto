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
package org.eclipse.ditto.services.utils.protocol.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the Ditto protocol adaption config.
 */
@Immutable
public final class DefaultProtocolConfig implements ProtocolConfig {

    private static final String CONFIG_PATH = "protocol";

    private final String provider;
    private final Set<String> blacklist;
    private final Set<String> incompatibleBlacklist;

    private DefaultProtocolConfig(final ScopedConfig scopedConfig) {
        provider = scopedConfig.getString(ProtocolConfigValue.PROVIDER.getConfigPath());
        blacklist = Collections.unmodifiableSet(
                new HashSet<>(scopedConfig.getStringList(ProtocolConfigValue.BLACKLIST.getConfigPath())));
        incompatibleBlacklist = Collections.unmodifiableSet(
                new HashSet<>(scopedConfig.getStringList(ProtocolConfigValue.INCOMPATIBLE_BLACKLIST.getConfigPath())));
    }

    /**
     * Returns an instance of {@code DefaultProtocolConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the permissions config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultProtocolConfig of(final Config config) {
        return new DefaultProtocolConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ProtocolConfigValue.values()));
    }

    @Override
    public String getProviderClassName() {
        return provider;
    }

    @Override
    public Set<String> getBlacklistedHeaderKeys() {
        return blacklist;
    }

    @Override
    public Set<String> getIncompatibleBlacklist() {
        return incompatibleBlacklist;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultProtocolConfig that = (DefaultProtocolConfig) o;
        return provider.equals(that.provider) &&
                blacklist.equals(that.blacklist) &&
                incompatibleBlacklist.equals(that.incompatibleBlacklist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, blacklist, incompatibleBlacklist);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "provider=" + provider +
                ", blacklist=" + blacklist +
                ", incompatibleBlacklist=" + incompatibleBlacklist +
                "]";
    }

}
