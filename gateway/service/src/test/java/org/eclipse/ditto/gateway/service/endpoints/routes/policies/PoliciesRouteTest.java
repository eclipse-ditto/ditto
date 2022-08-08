/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import static akka.http.javadsl.model.ContentTypes.APPLICATION_JSON;

import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResult;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;
import org.junit.Before;
import org.junit.Test;

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

    private PoliciesRoute policiesRoute;

    @Before
    public void setUp() {
        policiesRoute = new PoliciesRoute(routeBaseProperties,
                OAuthTokenIntegrationSubjectIdFactory.of(authConfig.getOAuthConfig()));
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
    public void getPolicyWithFields() {
        getRoute(getPreAuthResult()).run(HttpRequest.GET("/policies/org.eclipse.ditto%3Adummy?fields=_revision"))
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
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void activatePolicyTokenIntegrationWithPreAuthentication() {
        getRoute(getPreAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertEntity(PolicyActionFailedException.newBuilderForInappropriateAuthenticationMethod(
                                "activateTokenIntegration")
                        .build()
                        .toJsonString());
    }

    @Test
    public void deactivatePolicyTokenIntegrationWithPreAuthentication() {
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
    public void activateTopLevelTokenIntegration() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(TopLevelPolicyActionCommand.of(
                        ActivateTokenIntegration.of(PolicyId.of("ns:n"),
                                Label.of("-"),
                                List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                        SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
                                DummyJwt.EXPIRY,
                                DittoHeaders.empty()),
                        List.of()
                ).toJsonString());
    }

    @Test
    public void activateTopLevelTokenIntegrationWithUnresolvedJwtPlaceholder() {
        final JwtAuthenticationResult jwtAuthResultWithoutAudClaim =
                JwtAuthenticationResult.successful(DittoHeaders.empty(), getDummyAuthorizationContext(),
                        new DummyJwt() {
                            @Override
                            public JsonObject getBody() {
                                return super.getBody().toBuilder()
                                        .remove("aud")
                                        .build();
                            }
                        });
        getRoute(jwtAuthResultWithoutAudClaim).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/"))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertEntity(PolicyActionFailedException.newBuilder()
                        .action("activateTokenIntegration")
                        .status(HttpStatus.BAD_REQUEST)
                        .description("Mandatory placeholders could not be resolved, in detail: " +
                                "The placeholder 'jwt:aud' could not be resolved.")
                        .build()
                        .toJsonString());
    }

    @Test
    public void activateTopLevelTokenIntegrationWithAnnouncement() {
        final var subjectAnnouncement = SubjectAnnouncement.of(DittoDuration.parseDuration("1h"), true);
        final JsonObject requestPayload = JsonObject.newBuilder()
                .set("announcement", subjectAnnouncement.toJson())
                .build();
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/activateTokenIntegration/")
                        .withEntity(APPLICATION_JSON, requestPayload.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(TopLevelPolicyActionCommand.of(
                        ActivateTokenIntegration.of(PolicyId.of("ns:n"),
                                Label.of("-"),
                                List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                        SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
                                SubjectExpiry.newInstance(DummyJwt.EXPIRY),
                                subjectAnnouncement,
                                DittoHeaders.empty()),
                        List.of()
                ).toJsonString());
    }

    @Test
    public void deactivateTopLevelTokenIntegration() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST("/policies/ns%3An/actions/deactivateTokenIntegration"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(TopLevelPolicyActionCommand.of(
                        DeactivateTokenIntegration.of(PolicyId.of("ns:n"),
                                Label.of("-"),
                                List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                        SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
                                DittoHeaders.empty()),
                        List.of()
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
                .assertEntity(ActivateTokenIntegration.of(PolicyId.of("ns:n"),
                        Label.of("label"),
                        List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
                        DummyJwt.EXPIRY,
                        DittoHeaders.empty()
                ).toJsonString());
    }

    @Test
    public void activateTokenIntegrationForEntryWithAnnouncement() {
        final var subjectAnnouncement = SubjectAnnouncement.of(DittoDuration.parseDuration("1s"), true);
        final JsonObject requestPayload = JsonObject.newBuilder()
                .set("announcement", subjectAnnouncement.toJson())
                .build();
        getRoute(getTokenAuthResult()).run(HttpRequest.POST(
                                "/policies/ns%3An/entries/label/actions/activateTokenIntegration/")
                        .withEntity(APPLICATION_JSON, requestPayload.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(ActivateTokenIntegration.of(PolicyId.of("ns:n"),
                        Label.of("label"),
                        List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
                        SubjectExpiry.newInstance(DummyJwt.EXPIRY),
                        subjectAnnouncement,
                        DittoHeaders.empty()
                ).toJsonString());
    }

    @Test
    public void deactivateTokenIntegrationForEntry() {
        getRoute(getTokenAuthResult()).run(HttpRequest.POST(
                        "/policies/ns%3An/entries/label/actions/deactivateTokenIntegration"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DeactivateTokenIntegration.of(PolicyId.of("ns:n"),
                        Label.of("label"),
                        List.of(SubjectId.newInstance("integration:{{policy-entry:label}}:aud-1"),
                                SubjectId.newInstance("integration:{{policy-entry:label}}:aud-2")),
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
