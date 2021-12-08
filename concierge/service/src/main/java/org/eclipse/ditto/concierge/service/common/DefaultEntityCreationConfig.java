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
package org.eclipse.ditto.concierge.service.common;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link EntityCreationConfig} for Ditto's Concierge service.
 */
@Immutable
public class DefaultEntityCreationConfig implements EntityCreationConfig {

    private static final String CONFIG_PATH = "entity-creation";

    private final List<CreationRestrictionConfig> grant;
    private final List<CreationRestrictionConfig> revoke;

    private DefaultEntityCreationConfig(final ScopedConfig config) {
        grant = config.getConfigList(ConfigValue.GRANT.getConfigPath()).stream()
                .map(DefaultCreationRestrictionConfig::of)
                .collect(Collectors.toUnmodifiableList());
        revoke = config.getConfigList(ConfigValue.REVOKE.getConfigPath()).stream()
                .map(DefaultCreationRestrictionConfig::of)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns an instance of {@code DefaultEntityCreationConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the entity creation config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultEntityCreationConfig of(final Config config) {
        return new DefaultEntityCreationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, EntityCreationConfig.ConfigValue.values()));
    }

    @Override
    public List<CreationRestrictionConfig> getGrant() {
        return grant;
    }

    @Override
    public List<CreationRestrictionConfig> getRevoke() {
        return revoke;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultEntityCreationConfig that = (DefaultEntityCreationConfig) o;
        return grant.equals(that.grant) && revoke.equals(that.revoke);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grant, revoke);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "grant=" + grant +
                ", revoke=" + revoke +
                ']';
    }
}
