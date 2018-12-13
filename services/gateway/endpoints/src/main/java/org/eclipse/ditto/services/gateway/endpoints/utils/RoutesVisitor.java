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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;

/**
 * Visitor that modifies sub-routes of Gateway's HTTP interface.
 */
public interface RoutesVisitor {

    /**
     * Adjust Gateway's status route.
     *
     * @param route the current Gateway status route.
     * @return the new Gateway status route.
     */
    default StatusRoute ownStatusRoute(final StatusRoute route) {
        return route;
    }

    /**
     * Adjust status route for Ditto cluster.
     *
     * @param route the current cluster status route.
     * @return the new cluster status route.
     */
    default OverallStatusRoute overallStatusRoute(final OverallStatusRoute route) {
        return route;
    }

    /**
     * Adjust health route.
     *
     * @param route the current health route.
     * @return the new health route.
     */
    default CachingHealthRoute cachingHealthRoute(final CachingHealthRoute route) {
        return route;
    }

    /**
     * Adjust DevOps route.
     *
     * @param route the current DevOps route.
     * @return the new DevOps route.
     */
    default DevOpsRoute devopsRoute(final DevOpsRoute route) {
        return route;
    }

    /**
     * Adjust policies route.
     *
     * @param route the current policies route.
     * @return the new policies route.
     */
    default PoliciesRoute policiesRoute(final PoliciesRoute route) {
        return route;
    }

    /**
     * Adjsut server-send-event route.
     *
     * @param route the current SSE route.
     * @return the new SSE route.
     */
    default SseThingsRoute sseThingsRoute(final SseThingsRoute route) {
        return route;
    }

    /**
     * Adjust things route.
     *
     * @param route the current things route.
     * @return the new things route.
     */
    default ThingsRoute thingsRoute(final ThingsRoute route) {
        return route;
    }

    /**
     * Adjust search route.
     *
     * @param route the current search route.
     * @return the new search route.
     */
    default ThingSearchRoute thingSearchRoute(final ThingSearchRoute route) {
        return route;
    }

    /**
     * Adjust websocket route.
     *
     * @param route the current websocket route.
     * @return the new websocket route.
     */
    default WebsocketRoute websocketRoute(final WebsocketRoute route) {
        return route;
    }

    /**
     * Adjust stats route.
     *
     * @param route the current stats route.
     * @return the new stats route.
     */
    default StatsRoute statsRoute(final StatsRoute route) {
        return route;
    }
}
