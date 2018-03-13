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

/**
 * Common configuration of all services.
 */
public interface ServiceConfigReader {

    /**
     * Retrieve cluster configuration reader.
     *
     * @return cluster configuration of the service.
     */
    ClusterConfigReader getClusterConfigReader();

    /**
     * Retrieve statsd configuration reader.
     *
     * @return statsd configuration of the service.
     */
    StatsdConfigReader getStatsdConfigReader();

    /**
     * Retrieve the underlying {@code Config} object for backward compatibility.
     *
     * @return the underlying {@code Config} object.
     */
    Config getRawConfig();
}
