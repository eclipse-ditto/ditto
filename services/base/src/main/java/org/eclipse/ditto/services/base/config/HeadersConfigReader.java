/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base.config;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Configuration reader for the path {@code ditto.headers}.
 */
public final class HeadersConfigReader extends AbstractConfigReader {

    private static final String PATH = "ditto.headers";

    private HeadersConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a headers configuration reader from an unrelativized configuration object.
     *
     * @param rawConfig the raw configuration.
     * @return a headers configuration reader.
     */
    public static HeadersConfigReader fromRawConfig(final Config rawConfig) {
        final Config headersConfig = rawConfig.hasPath(PATH)
                ? rawConfig.getConfig(PATH)
                : ConfigFactory.empty();
        return new HeadersConfigReader(headersConfig);
    }

    /**
     * Return whether compatibility mode is on.
     *
     * @return whether compatibility mode is on.
     */
    public boolean compatibilityMode() {
        return getIfPresent("compatibility-mode", config::getBoolean)
                .orElse(false);
    }

    /**
     * Return headers that should not be published to clients.
     *
     * @return headers that should not be published.
     */
    public List<String> blacklist() {
        return compatibilityMode()
                ? compatibleBlacklist()
                : completeBlacklist();
    }

    private List<String> incompatibleBlacklist() {
        return getIfPresent("incompatible-blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }

    private List<String> compatibleBlacklist() {
        return getIfPresent("blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }

    private List<String> completeBlacklist() {
        return Stream.concat(compatibleBlacklist().stream(), incompatibleBlacklist().stream())
                .collect(Collectors.toList());
    }
}
