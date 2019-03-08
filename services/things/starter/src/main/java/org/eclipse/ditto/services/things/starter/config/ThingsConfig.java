/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.things.persistence.config.ThingConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithTagsConfig;

/**
 * Provides the configuration settings of the Things service.
 * <p>
 * Java serialization is supported for {@code ThingsConfig}.
 * </p>
 */
@Immutable
public interface ThingsConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig, WithTagsConfig {

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

        private ThingsConfigValue(final String thePath, final Object theDefaultValue) {
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
