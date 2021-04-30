/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.devops;

import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultDevOpsConfig;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirectiveFactory;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.base.api.devops.signals.commands.ExecutePiggybackCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Unit test for {@link DevOpsRoute}.
 */
public final class DevOpsRouteTest extends EndpointTestBase {

    private DevOpsRoute devOpsRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        final DevopsAuthenticationDirectiveFactory devopsAuthenticationDirectiveFactory =
                DevopsAuthenticationDirectiveFactory.newInstance(jwtAuthenticationFactory, getInsecureDevopsConfig());
        final DevopsAuthenticationDirective authenticationDirective = devopsAuthenticationDirectiveFactory.devops();
        devOpsRoute = new DevOpsRoute(createDummyResponseActor(), actorSystem, httpConfig, commandConfig,
                adapterProvider.getHttpHeaderTranslator(), authenticationDirective);

        final Route route = extractRequestContext(ctx -> devOpsRoute.buildDevOpsRoute(ctx, Collections.emptyMap()));
        underTest = testRoute(route);
    }

    @Test
    public void testPiggyback() {
        final RetrieveThing retrieveThing = RetrieveThing.of(ThingId.of("thing:id"), DittoHeaders.empty());
        final ExecutePiggybackCommand body =
                ExecutePiggybackCommand.of("things", "1", retrieveThing.toJson(), DittoHeaders.empty());
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body.toJsonString());
        final TestRouteResult result = underTest.run(HttpRequest.POST("/devops/piggyback")
                .withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void testPiggybackWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final String modifyAttribute = String.format("{\"type\":\"things.commands:modifyAttribute\"," +
                        "\"thingId\":\"thing:id\"," +
                        "\"attribute\":\"/attribute\"," +
                        "\"value\":%s}",
                tooLongNumber);
        final String executePiggyBack = String.format("{\"type\":\"devops.commands:executePiggybackCommand\"," +
                        "\"serviceName\":\"things\"," +
                        "\"instance\":null," +
                        "\"targetActorSelection\":\"1\"," +
                        "\"piggybackCommand\":%s}",
                modifyAttribute);
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, executePiggyBack);
        final TestRouteResult result = underTest.run(HttpRequest.POST("/devops/piggyback")
                .withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    private static DevOpsConfig getInsecureDevopsConfig() {
        return DefaultDevOpsConfig.of(ConfigFactory.parseString("devops.secured=false"));
    }

}
