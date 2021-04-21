/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.gateway.endpoints.routes.whoami;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Unit test for {@link WhoamiRoute}.
 */
public final class WhoamiRouteTest extends EndpointTestBase {

    @Rule
    public final TestName testName = new TestName();

    private WhoamiRoute whoamiRoute;
    private TestRoute underTest;
    private DittoHeaders dittoHeaders;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, system);

        final AuthorizationContext context = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, "any-google-user")),
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.INTEGRATION, "any-integration-subject")));
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .authorizationContext(context)
                .build();

        whoamiRoute = new WhoamiRoute(createDummyResponseActor(), system, httpConfig, commandConfig,
                adapterProvider.getHttpHeaderTranslator());
        final Route route = extractRequestContext(
                ctx -> whoamiRoute.buildWhoamiRoute(ctx, dittoHeaders));
        underTest = testRoute(route);
    }

    @Test
    public void forwardsWhoamiCommand() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/whoami"));

        final WhoamiResponse dummyResponse = WhoamiResponse.of(DefaultUserInformation.fromAuthorizationContext(
                getAuthContextWithPrefixedSubjectsFromHeaders(dittoHeaders)), dittoHeaders);

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity(dummyResponse.getEntity(JsonSchemaVersion.V_2).toString());
    }

    private static AuthorizationContext getAuthContextWithPrefixedSubjectsFromHeaders(final DittoHeaders headers) {
        final String authContextString = headers.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        final JsonObject authContextJson = authContextString == null ?
                JsonObject.empty() :
                JsonObject.of(authContextString);
        return AuthorizationModelFactory.newAuthContext(authContextJson);
    }

}
