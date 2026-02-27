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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryAllowedImportAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryImportable;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResources;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryAllowedImportAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryImportable;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResources;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.junit.Before;
import org.junit.Test;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.testkit.TestRoute;

/**
 * Tests {@link PolicyEntriesRoute}.
 */
public final class PolicyEntriesRouteTest extends EndpointTestBase {

    private static final PolicyId POLICY_ID = PolicyId.of("test", "policy");

    private static final String DUMMY_ENTRY_JSON = """
            {
              "subjects": {
                "google:the_subjectid": {
                  "type": "test"
                }
              },
              "resources": {
                "thing:/the_resource_path": {
                  "grant": ["READ"],
                  "revoke": []
                }
              }
            }""";

    private static final String DUMMY_ENTRIES_JSON = """
            {
              "the_label": {
                "subjects": {
                  "google:the_subjectid": {
                    "type": "test"
                  }
                },
                "resources": {
                  "thing:/the_resource_path": {
                    "grant": ["READ"],
                    "revoke": []
                  }
                }
              }
            }""";

    private static final String DUMMY_SUBJECTS_JSON = """
            {
              "google:the_subjectid": {
                "type": "test"
              }
            }""";

    private static final String DUMMY_SUBJECT_JSON = """
            {
              "type": "test"
            }""";

    private static final String DUMMY_RESOURCES_JSON = """
            {
              "thing:/the_resource_path": {
                "grant": ["READ"],
                "revoke": []
              }
            }""";

    private static final String DUMMY_RESOURCE_JSON = """
            {
              "grant": ["READ"],
              "revoke": []
            }""";

    private TestRoute underTest;

    @Before
    public void setUp() {
        final PolicyEntriesRoute policyEntriesRoute = new PolicyEntriesRoute(routeBaseProperties,
                OAuthTokenIntegrationSubjectIdFactory.of(authConfig.getOAuthConfig()));
        underTest = testRoute(handleExceptions(() ->
                extractRequestContext(ctx ->
                        policyEntriesRoute.buildPolicyEntriesRoute(ctx, dittoHeaders, POLICY_ID,
                                getPreAuthResult())
                )
        ));
    }

    @Test
    public void getPolicyEntries() {
        final var result = underTest.run(HttpRequest.GET("/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyEntries.TYPE);
    }

    @Test
    public void putPolicyEntries() {
        final var result = underTest.run(HttpRequest.PUT("/")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRIES_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntries.TYPE);
    }

    @Test
    public void postPolicyEntriesReturnsMethodNotAllowed() {
        final var result = underTest.run(HttpRequest.POST("/")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRIES_JSON));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getPolicyEntry() {
        final var result = underTest.run(HttpRequest.GET("/the_label"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyEntry.TYPE);
    }

    @Test
    public void putPolicyEntry() {
        final var result = underTest.run(HttpRequest.PUT("/the_label")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRY_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntry.TYPE);
    }

    @Test
    public void deletePolicyEntry() {
        final var result = underTest.run(HttpRequest.DELETE("/the_label"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), DeletePolicyEntry.TYPE);
    }

    @Test
    public void getSubjects() {
        final var result = underTest.run(HttpRequest.GET("/the_label/subjects"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveSubjects.TYPE);
    }

    @Test
    public void putSubjects() {
        final var result = underTest.run(HttpRequest.PUT("/the_label/subjects")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_SUBJECTS_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifySubjects.TYPE);
    }

    @Test
    public void getSubject() {
        final var result = underTest.run(HttpRequest.GET("/the_label/subjects/google:the_subjectid"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveSubject.TYPE);
    }

    @Test
    public void putSubject() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/subjects/google:the_subjectid")
                        .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_SUBJECT_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifySubject.TYPE);
    }

    @Test
    public void deleteSubject() {
        final var result = underTest.run(HttpRequest.DELETE("/the_label/subjects/google:the_subjectid"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), DeleteSubject.TYPE);
    }

    @Test
    public void getResources() {
        final var result = underTest.run(HttpRequest.GET("/the_label/resources"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveResources.TYPE);
    }

    @Test
    public void putResources() {
        final var result = underTest.run(HttpRequest.PUT("/the_label/resources")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_RESOURCES_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyResources.TYPE);
    }

    @Test
    public void getResource() {
        final var result = underTest.run(HttpRequest.GET("/the_label/resources/thing:/the_resource_path"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveResource.TYPE);
    }

    @Test
    public void putResource() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/resources/thing:/the_resource_path")
                        .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_RESOURCE_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyResource.TYPE);
    }

    @Test
    public void deleteResource() {
        final var result = underTest.run(
                HttpRequest.DELETE("/the_label/resources/thing:/the_resource_path"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), DeleteResource.TYPE);
    }

    @Test
    public void getAllowedImportAdditions() {
        final var result = underTest.run(HttpRequest.GET("/the_label/allowedImportAdditions"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyEntryAllowedImportAdditions.TYPE);
    }

    @Test
    public void putAllowedImportAdditions() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/allowedImportAdditions")
                        .withEntity(ContentTypes.APPLICATION_JSON, "[\"subjects\",\"resources\"]"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntryAllowedImportAdditions.TYPE);
    }

    @Test
    public void putAllowedImportAdditionsWithInvalidValue() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/allowedImportAdditions")
                        .withEntity(ContentTypes.APPLICATION_JSON, "[\"invalid\"]"));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getImportable() {
        final var result = underTest.run(HttpRequest.GET("/the_label/importable"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyEntryImportable.TYPE);
    }

    @Test
    public void putImportableImplicit() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/importable")
                        .withEntity(ContentTypes.APPLICATION_JSON, "\"implicit\""));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntryImportable.TYPE);
    }

    @Test
    public void putImportableExplicit() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/importable")
                        .withEntity(ContentTypes.APPLICATION_JSON, "\"explicit\""));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntryImportable.TYPE);
    }

    @Test
    public void putImportableNever() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/importable")
                        .withEntity(ContentTypes.APPLICATION_JSON, "\"never\""));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyEntryImportable.TYPE);
    }

    @Test
    public void putImportableWithInvalidValue() {
        final var result = underTest.run(
                HttpRequest.PUT("/the_label/importable")
                        .withEntity(ContentTypes.APPLICATION_JSON, "\"invalid\""));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getPolicyEntryWithTrailingSlash() {
        final var result = underTest.run(HttpRequest.GET("/the_label/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyEntry.TYPE);
    }

    @Test
    public void getSubjectsWithTrailingSlash() {
        final var result = underTest.run(HttpRequest.GET("/the_label/subjects/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveSubjects.TYPE);
    }

    @Test
    public void getResourcesWithTrailingSlash() {
        final var result = underTest.run(HttpRequest.GET("/the_label/resources/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrieveResources.TYPE);
    }

    private static AuthenticationResult getPreAuthResult() {
        return DefaultAuthenticationResult.successful(DittoHeaders.empty(),
                AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        AuthorizationSubject.newInstance("ditto:ditto")
                ));
    }

}
