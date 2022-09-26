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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.WithCleanupConfig;

/**
 * Provides configuration settings for policy entities.
 */
@Immutable
public interface PolicyConfig extends WithSupervisorConfig, WithActivityCheckConfig, WithSnapshotConfig,
        WithCleanupConfig {

    /**
     * Returns the config of the policy event journal behaviour.
     *
     * @return the config.
     */
    EventConfig getEventConfig();

    /**
     * Returns the configuration to which duration the {@code expiry} of a {@code Policy Subject} should be rounded up
     * to.
     * For example:
     * <ul>
     * <li>configured to "1 second": a received "expiry" is rounded up to the next full second</li>
     * <li>configured to "30 seconds": a received "expiry" is rounded up to the next half minute</li>
     * <li>configured to "1 hour": a received "expiry" is rounded up to the next full hour</li>
     * <li>configured to "12 hours": a received "expiry" is rounded up to the next half day</li>
     * <li>configured to "1 day": a received "expiry" is rounded up to the next full day</li>
     * <li>configured to "15 days": a received "expiry" is rounded up to the next half month</li>
     * </ul>
     *
     * @return the granularity to round up policy subject {@code expiry} timestamps to.
     */
    Duration getSubjectExpiryGranularity();

    /**
     * Returns the configuration to which duration the notify-before duration of each subject-expiry is rounded up.
     *
     * @return the granularity.
     */
    Duration getSubjectDeletionAnnouncementGranularity();

    /**
     * Return the class responsible for placeholder resolution in the subject ID of policy action commands.
     *
     * @return the class for subject resolution.
     */
    String getSubjectIdResolver();

    /**
     * Return the policy announcement config.
     *
     * @return the policy announcement config.
     */
    PolicyAnnouncementConfig getPolicyAnnouncementConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code PolicyConfig}.
     */
    enum PolicyConfigValue implements KnownConfigValue {

        /**
         * The granularity to round up policy subject {@code expiry} timestamps to.
         */
        SUBJECT_EXPIRY_GRANULARITY("subject-expiry-granularity", Duration.ofHours(1L)),

        /**
         * The granularity to round up notify-before duration of subject-expiry.
         */
        SUBJECT_DELETION_ANNOUNCEMENT_GRANULARITY("subject-deletion-announcement-granularity", Duration.ofMinutes(1L)),

        SUBJECT_ID_RESOLVER("subject-id-resolver",
                "org.eclipse.ditto.policies.service.persistence.actors.resolvers.DefaultSubjectIdFromActionResolver");

        private final String path;
        private final Object defaultValue;

        PolicyConfigValue(final String thePath, final Object theDefaultValue) {
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
