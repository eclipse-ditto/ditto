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
package org.eclipse.ditto.services.things.common.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.services.base.config.supervision.SupervisorConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the thing config.
 */
@Immutable
public final class DefaultThingConfig implements ThingConfig {

    private static final String CONFIG_PATH = "thing";

    private final SupervisorConfig supervisorConfig;
    private final ActivityCheckConfig activityCheckConfig;
    private final SnapshotConfig snapshotConfig;

    private DefaultThingConfig(final ScopedConfig scopedConfig) {
        supervisorConfig = DefaultSupervisorConfig.of(scopedConfig);
        activityCheckConfig = DefaultActivityCheckConfig.of(scopedConfig);
        snapshotConfig = DefaultSnapshotConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultThingConfig of(final Config config) {
        return new DefaultThingConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    @Override
    public SupervisorConfig getSupervisorConfig() {
        return supervisorConfig;
    }

    @Override
    public ActivityCheckConfig getActivityCheckConfig() {
        return activityCheckConfig;
    }

    @Override
    public SnapshotConfig getSnapshotConfig() {
        return snapshotConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultThingConfig that = (DefaultThingConfig) o;
        return Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(activityCheckConfig, that.activityCheckConfig) &&
                Objects.equals(snapshotConfig, that.snapshotConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supervisorConfig, activityCheckConfig, snapshotConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "supervisorConfig=" + supervisorConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                ", snapshotConfig=" + snapshotConfig +
                "]";
    }

}
