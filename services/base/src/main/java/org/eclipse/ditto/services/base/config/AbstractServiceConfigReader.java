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

public class AbstractServiceConfigReader extends AbstractConfigReader implements ServiceConfigReader {

    private static final String DEFAULT_CONFIG_PREFIX = "ditto";

    private final String prefix;
    private final String serviceName;

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
        super(config.getConfig(String.format("%s.%s", prefix, serviceName)));
        this.prefix = prefix;
        this.serviceName = serviceName;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public ClusterConfigReader getClusterConfigReader() {
        return new ClusterConfigReader(getChild("cluster"));
    }

    @Override
    public StatsdConfigReader getStatsdConfigReader() {
        return new StatsdConfigReader(getChild("statsd"));
    }
}
