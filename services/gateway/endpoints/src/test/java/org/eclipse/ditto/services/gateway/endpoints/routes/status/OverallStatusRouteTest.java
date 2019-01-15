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

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link OverallStatusRoute}.
 */
public class OverallStatusRouteTest extends EndpointTestBase {

    private static final String OVERALL_PATH = "/" + OverallStatusRoute.PATH_OVERALL;
    private static final String OVERALL_STATUS_PATH = OVERALL_PATH + "/" + OverallStatusRoute.PATH_STATUS;
    private static final String OVERALL_STATUS_HEALTH_PATH =
            OVERALL_STATUS_PATH + "/" + OverallStatusRoute.PATH_HEALTH;
    private static final String OVERALL_STATUS_CLUSTER_PATH =
            OVERALL_STATUS_PATH + "/" + OverallStatusRoute.PATH_CLUSTER;

    private TestRoute statusTestRoute;

    @Before
    public void setUp() {
        final Supplier<ClusterStatus> clusterStateSupplier = createClusterStatusSupplierMock();
        final StatusAndHealthProvider statusHealthProvider =
                DittoStatusAndHealthProviderFactory.of(system(), clusterStateSupplier);
        final OverallStatusRoute statusRoute =
                new OverallStatusRoute(clusterStateSupplier, statusHealthProvider);
        statusTestRoute = testRoute(statusRoute.buildOverallStatusRoute());
    }

    @Test
    public void getOverallStatusWithAuth() {
        // we need credentials here, because nginx allows all requests to /overall/*
        final TestRouteResult result =
                statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(OVERALL_STATUS_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatusWithAuth() {
        // we need credentials here, because nginx allows all requests to /overall/*
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(OVERALL_STATUS_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatusHealthWithAuth() {
        // we need credentials here, because nginx allows all requests to /overall/*
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(
                OVERALL_STATUS_HEALTH_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusOwnStatusClusterWithAuth() {
        // we need credentials here, because nginx allows all requests to /overall/*
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(
                OVERALL_STATUS_CLUSTER_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

}
