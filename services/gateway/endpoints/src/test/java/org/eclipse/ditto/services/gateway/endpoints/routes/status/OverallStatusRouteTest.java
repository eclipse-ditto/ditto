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
package org.eclipse.ditto.services.gateway.endpoints.routes.status;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link OverallStatusRoute}.
 */
public class OverallStatusRouteTest extends EndpointTestBase {

    private static final String STATUS_PATH = "/" + OverallStatusRoute.PATH_STATUS;
    private static final String STATUS_OWN_PATH = STATUS_PATH + "/" + OverallStatusRoute.PATH_OWN;
    private static final String STATUS_OWN_STATUS_PATH = STATUS_OWN_PATH + STATUS_PATH;
    private static final String STATUS_OWN_STATUS_HEALTH_PATH =
            STATUS_OWN_STATUS_PATH + "/" + OverallStatusRoute.PATH_HEALTH;
    private static final String STATUS_OWN_STATUS_CLUSTER_PATH =
            STATUS_OWN_STATUS_PATH + "/" + OverallStatusRoute.PATH_CLUSTER;
    private static final String STATUS_HEALTH_PATH = STATUS_PATH + "/" + OverallStatusRoute.PATH_HEALTH;
    private static final String STATUS_CLUSTER_PATH = STATUS_PATH + "/" + OverallStatusRoute.PATH_CLUSTER;

    private TestRoute statusTestRoute;

    @Before
    public void setUp() {
        final ActorRef healthCheckingActor = createHealthCheckingActorMock();
        final Supplier<ClusterStatus> clusterStateSupplier = createClusterStatusSupplierMock();
        final StatusAndHealthProvider statusHealthProvider = DittoStatusAndHealthProviderFactory.of(system(), clusterStateSupplier);
        final OverallStatusRoute statusRoute =
                new OverallStatusRoute(system(), clusterStateSupplier, healthCheckingActor, statusHealthProvider);
        statusTestRoute = testRoute(statusRoute.buildStatusRoute());
    }

    @Test
    public void getStatusWithAuth() {
        final TestRouteResult result =
                statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(STATUS_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatus() {
        // we don't need credentials here, because nginx denies all requests to /status/own*
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(STATUS_OWN_STATUS_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatusHealth() {
        // we don't need credentials here, because nginx denies all requests to /status/own*
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(STATUS_OWN_STATUS_HEALTH_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatusCluster() {
        // we don't need credentials here, because nginx denies all requests to /status/own*
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(STATUS_OWN_STATUS_CLUSTER_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusHealthWithAuth() {
        final TestRouteResult result =
                statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(STATUS_HEALTH_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusClusterWithAuth() {
        final TestRouteResult result =
                statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(STATUS_CLUSTER_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

}
