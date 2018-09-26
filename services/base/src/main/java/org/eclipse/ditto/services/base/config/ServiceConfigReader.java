/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
     * Retrieve a metrics configuration reader.
     *
     * @return the metrics configuration of the service.
     */
    MetricsConfigReader metrics();

    /**
     * Retrieve a mongo collection name suffix configuration reader.
     *
     * @return The reader.
     */
    SuffixBuilderConfigReader mongoCollectionNameSuffix();

    /**
     * Retrieve a limits configuration reader.
     *
     * @return the limits configuration of the service.
     */
    LimitsConfigReader limits();

    /**
     * Retrieve the underlying {@code Config} object for backward compatibility.
     *
     * @return the underlying {@code Config} object.
     */
    Config getRawConfig();
}
