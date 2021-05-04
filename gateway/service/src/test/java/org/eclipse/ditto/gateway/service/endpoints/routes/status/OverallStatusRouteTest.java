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
package org.eclipse.ditto.gateway.service.endpoints.routes.status;

import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.STATUS_CREDENTIALS;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.util.function.Supplier;

import org.eclipse.ditto.gateway.service.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.gateway.service.health.StatusAndHealthProvider;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirectiveFactory;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.junit.Before;
import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link OverallStatusRoute}.
 */
public final class OverallStatusRouteTest extends EndpointTestBase {

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
                DittoStatusAndHealthProviderFactory.of(system(), clusterStateSupplier, healthCheckConfig);
        final DevOpsConfig devOpsConfig = authConfig.getDevOpsConfig();
        final DevopsAuthenticationDirectiveFactory devopsAuthenticationDirectiveFactory =
                DevopsAuthenticationDirectiveFactory.newInstance(jwtAuthenticationFactory, devOpsConfig);
        final DevopsAuthenticationDirective authenticationDirective = devopsAuthenticationDirectiveFactory.status();
        final OverallStatusRoute statusRoute =
                new OverallStatusRoute(clusterStateSupplier, statusHealthProvider, authenticationDirective);
        statusTestRoute = testRoute(statusRoute.buildOverallStatusRoute());
    }

    @Test
    public void getOverallStatusWithAuth() {
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(OVERALL_STATUS_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getOverallStatusWithoutAuth() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(OVERALL_STATUS_PATH));
        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getOverallStatusHealthWithAuth() {
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(
                OVERALL_STATUS_HEALTH_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getOverallStatusHealthWithoutAuth() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(OVERALL_STATUS_HEALTH_PATH));
        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getOverallStatusClusterWithAuth() {
        final TestRouteResult result = statusTestRoute.run(withDevopsCredentials(HttpRequest.GET(
                OVERALL_STATUS_CLUSTER_PATH)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getOverallStatusClusterAsStatusUser() {
        final TestRouteResult result =
                statusTestRoute.run(HttpRequest.GET(OVERALL_STATUS_CLUSTER_PATH).addCredentials(STATUS_CREDENTIALS));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getOverallStatusClusterWithoutAuth() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(OVERALL_STATUS_CLUSTER_PATH));
        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = statusTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

}
