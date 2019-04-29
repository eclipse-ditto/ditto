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
     * Retrieve DevOps configuration reader.
     *
     * @return the DevOps configuration reader.
     */
    DevOpsConfigReader devops();

    /**
     * Retrieve the underlying {@code Config} object for backward compatibility.
     *
     * @return the underlying {@code Config} object.
     */
    Config getRawConfig();
}
