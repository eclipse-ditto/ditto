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
package org.eclipse.ditto.services.gateway.endpoints.routes.policies;

import static akka.http.javadsl.model.ContentTypes.APPLICATION_JSON;

import java.util.List;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyActionFailedException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResult;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubject;
import org.eclipse.ditto.signals.commands.policies.modify.ActivateSubjects;
import org.eclipse.ditto.signals.commands.policies.modify.DeactivateSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeactivateSubjects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;

/**
 * Tests {@link PoliciesRoute}.
 */
public final class PoliciesRouteTest extends EndpointTestBase {

    private static final String DUMMY_POLICY = "{\n" +
            "    \"entries\": {\n" +
            "      \"the_label\": {\n" +
            "        \"subjects\": {\n" +
            "          \"google:the_subjectid\": {\n" +
            "            \"type\": \"yourSubjectTypeDescription\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"resources\": {\n" +
            "          \"thing:/the_resource_path\": {\n" +
            "            \"grant\": [\n" +
            "              \"READ\",\n" +
            "              \"WRITE\"\n" +
            "            ],\n" +
            "            \"revoke\": []\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }";

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private PoliciesRoute policiesRoute;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        policiesRoute = new PoliciesRoute(createDummyResponseActor(), actorSystem, httpConfig, commandConfig,
                adapterProvider.getHttpHeaderTranslator(),
                OAuthTokenIntegrationSubjectIdFactory.of(authConfig.getOAuthConfig()));

        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

    }

    @Test
    public void putPolicy() {
        getRoute(getPreAuthResult()).run(HttpRequest.PUT("/policies/org.eclipse.ditto%3Adummy")
                .withEntity(APPLICATION_JSON, DUMMY_POLICY))
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getPolicy() {
        getRoute(getPreAuthResult()).run(HttpRequest.GET("/policies/org.eclipse.ditto%3Adummy"))
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void deletePolicy() {
        getRoute(getPreAuthResult()).run(HttpRequest.DELETE("/policies/org.eclipse.ditto%3Adummy"))
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void postPolicy() {
        getRoute(getPreAuthResult()).run(HttpRequest.POST("/policies")
                .withEntity(APPLICATION_JSON, DUMMY_POLICY))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void activateSubjectsWithPreAuthentication() {
        getRoute(getPreAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertEntity(PolicyActionFailedException.newBuilderForInappropriateAuthenticationMethod(
                        "activateTokenIntegration")
                        .build()
                        .toJsonString());
    }

    @Test
    public void deactivateSubjectsWithPreAuthentication() {
        getRoute(getPreAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/deactivateTokenIntegration"))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertEntity(PolicyActionFailedException.newBuilderForInappropriateAuthenticationMethod(
                        "deactivateTokenIntegration")
                        .build()
                        .toJsonString());
    }

    @Test
    public void incorrectPolicyActionMethod() {
        getRoute(getTokenAuthResult()).run(HttpRequest.GET("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void nonexistentPolicyAction() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/justDoIt"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void activateTokenIntegration() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(ActivateSubjects.of(PolicyId.of("ns:n"),
                        SubjectId.newInstance("dummy-issuer:{{policy-entry:label}}:dummy-subject"),
                        DummyJwt.EXPIRY,
                        List.of(),
                        DittoHeaders.empty()
                ).toJsonString());
    }

    @Test
    public void deactivateTokenIntegration() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/deactivateTokenIntegration"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DeactivateSubjects.of(PolicyId.of("ns:n"),
                        SubjectId.newInstance("dummy-issuer:{{policy-entry:label}}:dummy-subject"),
                        List.of(),
                        DittoHeaders.empty()
                ).toJsonString());
    }

    private TestRoute getRoute(final AuthenticationResult authResult) {
        return testRoute(handleExceptions(() ->
                extractRequestContext(ctx ->
                        policiesRoute.buildPoliciesRoute(ctx, dittoHeaders, authResult))
        ));
    }

    @Test
    public void activateTokenIntegrationForEntry() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST(
                "/policies/ns%3An/entries/label/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(ActivateSubject.of(PolicyId.of("ns:n"),
                        Label.of("label"),
                        SubjectId.newInstance("dummy-issuer:{{policy-entry:label}}:dummy-subject"),
                        DummyJwt.EXPIRY,
                        DittoHeaders.empty()
                ).toJsonString());
    }

    @Test
    public void deactivateTokenIntegrationForEntry() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST(
                "/policies/ns%3An/entries/label/actions/deactivateTokenIntegration"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DeactivateSubject.of(PolicyId.of("ns:n"),
                        Label.of("label"),
                        SubjectId.newInstance("dummy-issuer:{{policy-entry:label}}:dummy-subject"),
                        DittoHeaders.empty()
                ).toJsonString());
    }

    private static AuthorizationContext getDummyAuthorizationContext() {
        return AuthorizationContext.newInstance(
                DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                AuthorizationSubject.newInstance("ditto:ditto")
        );
    }

    private static AuthenticationResult getPreAuthResult() {
        return DefaultAuthenticationResult.successful(DittoHeaders.empty(), getDummyAuthorizationContext());
    }

    private static JwtAuthenticationResult getTokenAuthResult() {
        return JwtAuthenticationResult.successful(DittoHeaders.empty(), getDummyAuthorizationContext(),
                new DummyJwt());
    }
}