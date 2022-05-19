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
package org.eclipse.ditto.internal.models.signalenrichment;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides configuration settings for Connectivity service's enrichment.
 */
@Immutable
public interface SignalEnrichmentConfig {

    /**
     * Relative path of a signal-enrichment config.
     */
    String CONFIG_PATH = "signal-enrichment";

    /**
     * Returns the configuration for the signal-enrichment facade provider.
     *
     * @return the configuration.
     */
    Config getProviderConfig();

    /**
     * Render this object as a {@code Config}.
     *
     * @return the rendered {@code Config} object.
     */
    Config render();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SignalEnrichmentConfig}.
     */
    enum SignalEnrichmentConfigValue implements KnownConfigValue {

        /**
         * Configuration for the provider.
         */
        PROVIDER_CONFIG("provider-config", ConfigFactory.empty().root());

        private final String path;
        private final Object defaultValue;

        SignalEnrichmentConfigValue(final String thePath, final Object theDefaultValue) {
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
