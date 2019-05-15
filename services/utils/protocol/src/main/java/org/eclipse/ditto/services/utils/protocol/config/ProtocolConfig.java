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

import java.util.Arrays;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the Ditto protocol adaption.
 * <p>
 * Java serialization is supported for {@code ProtocolConfig}.
 * </p>
 */
@Immutable
public interface ProtocolConfig {

    /**
     * Returns the class name of the provider of the Ditto protocol adapter.
     *
     * @return the class name.
     */
    String getProviderClassName();

    /**
     * Returns the keys of headers that should be ignored by Ditto.
     *
     * @return an unmodifiable unsorted Set containing the blacklisted header keys.
     */
    Set<String> getBlacklistedHeaderKeys();

    /**
     * Returns the keys of headers that are required by any previous version of Ditto protocol adapter.
     *
     * @return an unmodifiable unsorted Set containing the header keys.
     */
    Set<String> getIncompatibleBlacklist();

    enum ProtocolConfigValue implements KnownConfigValue {

        /**
         * The class name of the provider of the Ditto protocol adapter.
         */
        PROVIDER("provider", "org.eclipse.ditto.services.utils.protocol.DittoProtocolAdapterProvider"),

        /**
         * The keys of headers that should be ignored by Ditto.
         */
        BLACKLIST("blacklist",
                Arrays.asList("authorization", "raw-request-uri", "cache-control", "connection", "timeout-access")),

        /**
         * The keys of headers that are required by any previous version of Ditto protocol adapter.
         */
        INCOMPATIBLE_BLACKLIST("incompatible-blacklist",
                Arrays.asList("subject", "direction", "status", "thing-id", "feature-id"));

        private final String path;
        private final Object defaultValue;

        private ProtocolConfigValue(final String thePath, final Object theDefaultValue) {
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
