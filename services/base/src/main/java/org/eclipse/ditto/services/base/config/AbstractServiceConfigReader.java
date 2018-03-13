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

public class AbstractServiceConfigReader extends AbstractConfigReader implements ServiceConfigReader {

    private static final String DEFAULT_CONFIG_PREFIX = "ditto";

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
        super(getOrEmpty(config, String.format("%s.%s", prefix, serviceName)));
        this.rawConfig = config;
    }

    @Override
    public Config getRawConfig() {
        return rawConfig;
    }

    @Override
    public ClusterConfigReader getClusterConfigReader() {
        return new ClusterConfigReader(getChild("cluster"));
    }

    @Override
    public StatsdConfigReader getStatsdConfigReader() {
        return new StatsdConfigReader(getChild("statsd"));
    }

    private static Config getOrEmpty(final Config config, final String path) {
        return config.hasPath(path) ? config.getConfig(path) : ConfigFactory.empty();
    }
}
