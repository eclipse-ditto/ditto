/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mapper;

/**
 * The topic path shared by the cross-Thing timeseries command and its response.
 * <p>
 * Cross-Thing aggregation names a namespace but no entity, so the entity-name position carries the
 * {@code _} placeholder — the same convention {@code CheckPermissions} uses for a command that
 * targets no single entity. Keeping both sides of the exchange on one definition means the request
 * and reply cannot drift apart.
 *
 * @since 4.0.0
 */
final class TimeseriesAggregateTopicPath {

    /** Entity-name placeholder for a command that targets a namespace rather than one entity. */
    private static final String NO_ENTITY_NAME = "_";

    private static final String SUFFIX = "/things/twin/timeseries/aggregate";

    private TimeseriesAggregateTopicPath() {
        throw new AssertionError();
    }

    /**
     * @param namespace the namespace being aggregated.
     * @return the topic path string {@code <namespace>/_/things/twin/timeseries/aggregate}.
     */
    static String forNamespace(final String namespace) {
        return namespace + "/" + NO_ENTITY_NAME + SUFFIX;
    }
}
