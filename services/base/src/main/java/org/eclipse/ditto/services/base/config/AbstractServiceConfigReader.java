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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Abstract base implementation of {@link ServiceConfigReader}.
 */
public class AbstractServiceConfigReader extends AbstractConfigReader implements ServiceConfigReader {

    private static final String DEFAULT_CONFIG_PREFIX = "ditto";
    private static final String PATH_CLUSTER = "cluster";
    private static final String PATH_HEALTH_CHECK = "health-check";
    private static final String PATH_HTTP = "http";
    private static final String PATH_STATSD = "statsd";

    private final Config rawConfig;

    /**
     * Create a service config reader with the default config prefix.
     *
     * @param config Overall config.
     * @param serviceName Name of service.
     */
    protected AbstractServiceConfigReader(final Config config, final String serviceName) {
        this(config, DEFAULT_CONFIG_PREFIX, serviceName);
    }

    /**
     * Create a service config reader.
     *
     * @param config Overall config.
     * @param prefix Prefix of service config.
     * @param serviceName Name of service.
     */
    protected AbstractServiceConfigReader(final Config config, final String prefix, final String serviceName) {
        super(getOrEmpty(config, path(prefix, serviceName)));
        this.rawConfig = config;
    }

    @Override
    public Config getRawConfig() {
        return rawConfig;
    }

    @Override
    public ClusterConfigReader cluster() {
        return new ClusterConfigReader(getChildOrEmpty(PATH_CLUSTER));
    }

    @Override
    public HealthConfigReader health() {
        return new HealthConfigReader(getChildOrEmpty(PATH_HEALTH_CHECK));
    }

    @Override
    public HttpConfigReader http() {
        return new HttpConfigReader(getChildOrEmpty(PATH_HTTP));
    }

    @Override
    public StatsdConfigReader statsd() {
        return new StatsdConfigReader(getChildOrEmpty(PATH_STATSD));
    }

    private static Config getOrEmpty(final Config config, final String path) {
        return config.hasPath(path) ? config.getConfig(path) : ConfigFactory.empty();
    }
}
