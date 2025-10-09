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
package org.eclipse.ditto.things.service.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.base.service.config.supervision.SupervisorConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.CleanupConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the thing config.
 */
@Immutable
public final class DefaultThingConfig implements ThingConfig {

    private static final String CONFIG_PATH = "thing";

    private final Duration shutdownTimeout;
    private final SupervisorConfig supervisorConfig;
    private final ActivityCheckConfig activityCheckConfig;
    private final SnapshotConfig snapshotConfig;
    private final ThingEventConfig eventConfig;
    private final ThingMessageConfig messageConfig;
    private final CleanupConfig cleanupConfig;
    private final boolean mergeRemoveEmptyObjectsAfterPatchConditionFiltering;

    private DefaultThingConfig(final ScopedConfig scopedConfig) {
        shutdownTimeout = scopedConfig.getDuration(ConfigValue.SHUTDOWN_TIMEOUT.getConfigPath());
        supervisorConfig = DefaultSupervisorConfig.of(scopedConfig);
        activityCheckConfig = DefaultActivityCheckConfig.of(scopedConfig);
        snapshotConfig = DefaultSnapshotConfig.of(scopedConfig);
        eventConfig = DefaultThingEventConfig.of(scopedConfig);
        messageConfig = DefaultThingMessageConfig.of(scopedConfig);
        cleanupConfig = CleanupConfig.of(scopedConfig);
        mergeRemoveEmptyObjectsAfterPatchConditionFiltering = scopedConfig.getBoolean(
                ThingConfig.ConfigValue.MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING.getConfigPath());
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultThingConfig of(final Config config) {
        return new DefaultThingConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
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
    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    @Override
    public ThingEventConfig getEventConfig() {
        return eventConfig;
    }

    @Override
    public ThingMessageConfig getMessageConfig() {
        return messageConfig;
    }

    @Override
    public boolean isMergeRemoveEmptyObjectsAfterPatchConditionFiltering() {
        return mergeRemoveEmptyObjectsAfterPatchConditionFiltering;
    }

    @Override
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
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
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(eventConfig, that.eventConfig) &&
                Objects.equals(cleanupConfig, that.cleanupConfig) &&
                Objects.equals(shutdownTimeout, that.shutdownTimeout) &&
                mergeRemoveEmptyObjectsAfterPatchConditionFiltering ==
                        that.mergeRemoveEmptyObjectsAfterPatchConditionFiltering;
    }

    @Override
    public int hashCode() {
        return Objects.hash(supervisorConfig, activityCheckConfig, snapshotConfig, eventConfig, messageConfig,
                cleanupConfig, shutdownTimeout, mergeRemoveEmptyObjectsAfterPatchConditionFiltering);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "supervisorConfig=" + supervisorConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                ", snapshotConfig=" + snapshotConfig +
                ", eventConfig=" + eventConfig +
                ", messageConfig=" + messageConfig +
                ", cleanupConfig=" + cleanupConfig +
                ", shutdownTimeout=" + shutdownTimeout +
                ", mergeRemoveEmptyObjectsAfterPatchConditionFiltering=" +
                mergeRemoveEmptyObjectsAfterPatchConditionFiltering +
                "]";
    }
}
