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
package org.eclipse.ditto.policies.service.common.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.base.service.config.supervision.SupervisorConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultEventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultNamespaceActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.NamespaceActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.CleanupConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of the policy config.
 */
@Immutable
public final class DefaultPolicyConfig implements PolicyConfig {

    private static final String CONFIG_PATH = "policy";
    private static final String NAMESPACE_ACTIVITY_CHECK_CONFIG_PATH = "namespace-activity-check";

    private final SupervisorConfig supervisorConfig;
    private final ActivityCheckConfig activityCheckConfig;
    private final List<NamespaceActivityCheckConfig> namespaceActivityCheckConfigs;
    private final SnapshotConfig snapshotConfig;
    private final EventConfig eventConfig;
    private final Duration policySubjectExpiryGranularity;
    private final Duration policySubjectDeletionAnnouncementGranularity;
    private final String subjectIdResolver;
    private final PolicyAnnouncementConfig policyAnnouncementConfig;
    private final CleanupConfig cleanupConfig;

    private DefaultPolicyConfig(final ScopedConfig scopedConfig) {
        supervisorConfig = DefaultSupervisorConfig.of(scopedConfig);
        activityCheckConfig = DefaultActivityCheckConfig.of(scopedConfig);
        namespaceActivityCheckConfigs = loadNamespaceActivityCheckConfigs(scopedConfig);
        snapshotConfig = DefaultSnapshotConfig.of(scopedConfig);
        eventConfig = DefaultEventConfig.of(scopedConfig);
        policySubjectExpiryGranularity =
                scopedConfig.getNonNegativeDurationOrThrow(PolicyConfigValue.SUBJECT_EXPIRY_GRANULARITY);
        policySubjectDeletionAnnouncementGranularity =
                scopedConfig.getNonNegativeDurationOrThrow(
                        PolicyConfigValue.SUBJECT_DELETION_ANNOUNCEMENT_GRANULARITY);
        subjectIdResolver = scopedConfig.getString(PolicyConfigValue.SUBJECT_ID_RESOLVER.getConfigPath());
        policyAnnouncementConfig = PolicyAnnouncementConfig.of(scopedConfig);
        cleanupConfig = CleanupConfig.of(scopedConfig);
    }

    private static List<NamespaceActivityCheckConfig> loadNamespaceActivityCheckConfigs(final ScopedConfig config) {
        if (config.hasPath(NAMESPACE_ACTIVITY_CHECK_CONFIG_PATH)) {
            final var configList = config.getList(NAMESPACE_ACTIVITY_CHECK_CONFIG_PATH);
            final List<NamespaceActivityCheckConfig> result = new ArrayList<>(configList.size());
            for (final var configValue : configList) {
                result.add(DefaultNamespaceActivityCheckConfig.of(
                        ConfigFactory.empty().withFallback(configValue)));
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    /**
     * Returns an instance of the policy config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the policy config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPolicyConfig of(final Config config) {
        final var mappingScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PolicyConfigValue.values());

        return new DefaultPolicyConfig(mappingScopedConfig);
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
    public EventConfig getEventConfig() {
        return eventConfig;
    }

    @Override
    public Duration getSubjectExpiryGranularity() {
        return policySubjectExpiryGranularity;
    }

    @Override
    public Duration getSubjectDeletionAnnouncementGranularity() {
        return policySubjectDeletionAnnouncementGranularity;
    }

    @Override
    public String getSubjectIdResolver() {
        return subjectIdResolver;
    }

    @Override
    public PolicyAnnouncementConfig getPolicyAnnouncementConfig() {
        return policyAnnouncementConfig;
    }

    @Override
    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    @Override
    public List<NamespaceActivityCheckConfig> getNamespaceActivityCheckConfigs() {
        return namespaceActivityCheckConfigs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPolicyConfig that = (DefaultPolicyConfig) o;
        return Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(activityCheckConfig, that.activityCheckConfig) &&
                Objects.equals(namespaceActivityCheckConfigs, that.namespaceActivityCheckConfigs) &&
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(eventConfig, that.eventConfig) &&
                Objects.equals(policySubjectExpiryGranularity, that.policySubjectExpiryGranularity) &&
                Objects.equals(policySubjectDeletionAnnouncementGranularity,
                        that.policySubjectDeletionAnnouncementGranularity) &&
                Objects.equals(subjectIdResolver, that.subjectIdResolver) &&
                Objects.equals(policyAnnouncementConfig, that.policyAnnouncementConfig) &&
                Objects.equals(cleanupConfig, that.cleanupConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supervisorConfig, activityCheckConfig, namespaceActivityCheckConfigs, snapshotConfig,
                eventConfig, policySubjectExpiryGranularity, policySubjectDeletionAnnouncementGranularity,
                subjectIdResolver, policyAnnouncementConfig, cleanupConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                " supervisorConfig=" + supervisorConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                ", namespaceActivityCheckConfigs=" + namespaceActivityCheckConfigs +
                ", snapshotConfig=" + snapshotConfig +
                ", eventConfig=" + eventConfig +
                ", policySubjectExpiryGranularity=" + policySubjectExpiryGranularity +
                ", policySubjectDeletionAnnouncementGranularity=" + policySubjectDeletionAnnouncementGranularity +
                ", subjectIdResolver=" + subjectIdResolver +
                ", policyAnnouncementConfig=" + policyAnnouncementConfig +
                ", cleanUpConfig=" + cleanupConfig +
                "]";
    }
}
