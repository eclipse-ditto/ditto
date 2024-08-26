/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.thingsearch.service.starter.actors;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Things Aggregate.
 */
@Immutable
public final class ThingsAggregationConstants {


    /**
     * Name of the pekko cluster role.
     */
    public static final String CLUSTER_ROLE = "search";

    private static final String PATH_DELIMITER = "/";

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    /**
     * Name of the Aggregate actor
     */
    public static final String AGGREGATE_ACTOR_NAME = "aggregateThingsMetrics";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsAggregationConstants() {
        // no-op
    }
}
