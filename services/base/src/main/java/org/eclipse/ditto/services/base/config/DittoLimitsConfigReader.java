/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.base.config;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Limits configurations.
 */
public final class DittoLimitsConfigReader extends AbstractConfigReader implements LimitsConfigReader {

    private static final long DEFAULT_ENTITY_MAX_SIZE = 100 * 1024L;
    private static final long DEFAULT_MESSAGE_MAX_SIZE = 250 * 1024L;

    private static final int DEFAULT_THINGS_SEARCH_DEFAULT_PAGE_SIZE = 25;
    private static final int DEFAULT_THINGS_SEARCH_MAX_PAGE_SIZE = 200;

    private static final String PATH_THINGS_MAX_SIZE = "things.max-size";
    private static final String PATH_POLICIES_MAX_SIZE = "policies.max-size";
    private static final String PATH_MESSAGES_MAX_SIZE = "messages.max-size";

    private static final String THINGS_SEARCH_KEY = "things-search";
    private static final String PATH_THINGS_SEARCH_DEFAULT_PAGE_SIZE = path(THINGS_SEARCH_KEY, "default-page-size");
    private static final String PATH_THINGS_SEARCH_MAX_PAGE_SIZE = path(THINGS_SEARCH_KEY, "max-page-size");

    DittoLimitsConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a limits configuration reader from an unrelativized configuration object.
     *
     * @param rawConfig the raw configuration.
     * @return a limits configuration reader.
     */
    public static DittoLimitsConfigReader fromRawConfig(final Config rawConfig) {
        final Config headersConfig = rawConfig.hasPath(PATH)
                ? rawConfig.getConfig(PATH)
                : ConfigFactory.empty();
        return new DittoLimitsConfigReader(headersConfig);
    }

    @Override
    public long thingsMaxSize() {
        return getIfPresent(PATH_THINGS_MAX_SIZE, config::getBytes).orElse(DEFAULT_ENTITY_MAX_SIZE);
    }

    @Override
    public long policiesMaxSize() {
        return getIfPresent(PATH_POLICIES_MAX_SIZE, config::getBytes).orElse(DEFAULT_ENTITY_MAX_SIZE);
    }

    @Override
    public long messagesMaxSize() {
        return getIfPresent(PATH_MESSAGES_MAX_SIZE, config::getBytes).orElse(DEFAULT_MESSAGE_MAX_SIZE);
    }

    @Override
    public int thingsSearchDefaultPageSize() {
        return getIfPresent(PATH_THINGS_SEARCH_DEFAULT_PAGE_SIZE, config::getInt)
                .orElse(DEFAULT_THINGS_SEARCH_DEFAULT_PAGE_SIZE);
    }

    @Override
    public int thingsSearchMaxPageSize() {
        return getIfPresent(PATH_THINGS_SEARCH_MAX_PAGE_SIZE, config::getInt)
                .orElse(DEFAULT_THINGS_SEARCH_MAX_PAGE_SIZE);
    }

}
