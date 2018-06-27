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
     * Retrieve a cluster configuration reader.
     *
     * @return the cluster configuration of the service.
     */
    ClusterConfigReader cluster();

    /**
     * Get the index of this service instance.
     *
     * @return the instance index.
     */
    default int instanceIndex() {
        return cluster().instanceIndex();
    }

    /**
     * Retrieve a health configuration reader.
     *
     * @return the health configuration of the service.
     */
    HealthConfigReader health();

    /**
     * Retrieve a HTTP-service configuration reader.
     *
     * @return the HTTP-service configuration of the service.
     */
    HttpConfigReader http();

    /**
     * Retrieve a statsd configuration reader.
     *
     * @return the statsd configuration of the service.
     */
    StatsdConfigReader statsd();

    /**
     * Retrieve the underlying {@code Config} object for backward compatibility.
     *
     * @return the underlying {@code Config} object.
     */
    Config getRawConfig();
}
