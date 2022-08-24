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
package org.eclipse.ditto.policies.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings for policy entities.
 */
@Immutable
public interface PolicyAnnouncementConfig {

    /**
     * Returns the grace period where an expired subject is allowed to remain in the policy when its announcements are
     * not acknowledged.
     *
     * @return the grace period.
     */
    Duration getGracePeriod();

    /**
     * Returns the maximum timeout of acknowledgement aggregation.
     *
     * @return the maximum timeout.
     */
    Duration getMaxTimeout();

    /**
     * Returns whether announcements are published for subjects upon deletion when requested.
     *
     * @return whether announcements-when-deleted are enabled.
     */
    boolean isEnableAnnouncementsWhenDeleted();

    /**
     * Returns the default randomization interval.
     *
     * @return the default interval in which an announcement is sent earlier.
     * @since 3.0.0
     */
    Duration getDefaultRandomizationInterval();

    /**
     * Returns the config for the exponential back-off strategy of announcement redelivery.
     *
     * @return the config.
     */
    ExponentialBackOffConfig getExponentialBackOffConfig();

    /**
     * Returns an instance of the policy announcement config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the policy config at "announcement"
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static PolicyAnnouncementConfig of(final Config config) {
        return DefaultPolicyAnnouncementConfig.of(config);
    }


    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The grace period.
         */
        GRACE_PERIOD("grace-period", Duration.ofHours(4L)),

        /**
         * The maximum timeout.
         */
        MAX_TIMEOUT("max-timeout", Duration.ofMinutes(1L)),

        /**
         * The maximum duration of randomization.
         */
        DEFAULT_RANDOMIZATION_INTERVAL("default-randomization-interval", Duration.ofMinutes(5)),

        /**
         * Whether when-deleted announcements are enabled.
         */
        ENABLE_ANNOUNCEMENTS_WHEN_DELETED("enable-announcements-when-deleted", true);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
