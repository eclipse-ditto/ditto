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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.WithPersistenceOperationsConfig;
import org.eclipse.ditto.wot.integration.config.WotConfig;

/**
 * Provides the configuration settings of the Things service.
 */
@Immutable
public interface ThingsConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithPersistenceOperationsConfig,
        WithMongoDbConfig {

    /**
     * Indicates whether minimal information for all incoming messages should be logged.
     * This enables message tracing throughout the system.
     *
     * @return {@code true} if all incoming messages should be logged, {@code false} else.
     */
    boolean isLogIncomingMessages();

    /**
     * Returns the configuration settings for thing entities.
     *
     * @return the config.
     */
    ThingConfig getThingConfig();

    /**
     * Returns the WoT (Web of Things) related configuration setting to apply in the things service.
     *
     * @return the WoT config.
     */
    WotConfig getWotConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code ThingsConfig}.
     */
    enum ThingsConfigValue implements KnownConfigValue {

        /**
         * Determines whether minimal information for all incoming messages should be logged.
         * This enables message tracing throughout the system.
         */
        LOG_INCOMING_MESSAGES("log-incoming-messages", true);

        private final String path;
        private final Object defaultValue;

        ThingsConfigValue(final String thePath, final Object theDefaultValue) {
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
