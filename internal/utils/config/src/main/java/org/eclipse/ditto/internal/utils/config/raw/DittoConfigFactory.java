/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.config.raw;

import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Creates a {@link Config} based on a given resource or a given file enhanced by config from secrets and optionally
 * from VCAP_SERVICES.
 */
final class DittoConfigFactory {

    private DittoConfigFactory() {
        // No-Op because it's a static factory.
    }

    /**
     * Creates a {@link Config} based on a given resource (e.g. 'things-dev') enhanced by config from secrets and
     * optionally from VCAP_SERVICES.
     *
     * @param resourceBaseName the name of the config file without the file ending.
     * @return the config.
     */
    static Config fromResource(final String resourceBaseName) {
        final Config initialConfig = ConfigFactory.parseResourcesAnySyntax(resourceBaseName);
        return enhanceWithFallbacks(initialConfig);
    }

    /**
     * Creates a {@link Config} based on a given file enhanced by config from secrets and
     * optionally from VCAP_SERVICES.
     *
     * @param file the file containing the config.
     * @return the config.
     */
    static Config fromFile(final File file) {
        final Config initialConfig = ConfigFactory.parseFileAnySyntax(file);
        return enhanceWithFallbacks(initialConfig);
    }

    private static Config enhanceWithFallbacks(final Config initialConfig) {
        final Config config = VcapServicesStringSupplier.getInstance()
                .flatMap(VcapServicesStringSupplier::get)
                .map(VcapServicesStringToConfig.getInstance())
                .map(initialConfig::withFallback)
                .orElse(initialConfig);
        return config.withFallback(SecretsAsConfigSupplier.getInstance(config).get());
    }

}
