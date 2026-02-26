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

import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;
import org.junit.Before;
import org.junit.Test;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.testkit.TestRoute;

/**
 * Tests {@link PolicyImportsRoute}.
 */
public final class PolicyImportsRouteTest extends EndpointTestBase {

    private static final PolicyId POLICY_ID = PolicyId.of("test", "policy");

    private static final String IMPORTED_POLICY_ID = "test:imported";

    private static final String DUMMY_IMPORT_JSON = """
            {
              "entries": ["the_label"]
            }""";

    private static final String DUMMY_IMPORTS_JSON = """
            {
              "test:imported": {
                "entries": ["the_label"]
              }
            }""";

    private static final String DUMMY_ENTRIES_JSON = "[\"the_label\",\"other_label\"]";

    private static final String DUMMY_ENTRIES_ADDITIONS_JSON = """
            {
              "the_label": {
                "subjects": {
                  "google:someUser": {
                    "type": "test"
                  }
                }
              }
            }""";

    private static final String DUMMY_ENTRY_ADDITION_JSON = """
            {
              "subjects": {
                "google:someUser": {
                  "type": "test"
                }
              }
            }""";

    private TestRoute underTest;

    @Before
    public void setUp() {
        final PolicyImportsRoute policyImportsRoute = new PolicyImportsRoute(routeBaseProperties);
        underTest = testRoute(handleExceptions(() ->
                extractRequestContext(ctx ->
                        policyImportsRoute.buildPolicyImportsRoute(ctx, dittoHeaders, POLICY_ID)
                )
        ));
    }

    @Test
    public void getPolicyImports() {
        final var result = underTest.run(HttpRequest.GET("/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImports.TYPE);
    }

    @Test
    public void putPolicyImports() {
        final var result = underTest.run(HttpRequest.PUT("/")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_IMPORTS_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyImports.TYPE);
    }

    @Test
    public void postPolicyImportsReturnsMethodNotAllowed() {
        final var result = underTest.run(HttpRequest.POST("/")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_IMPORTS_JSON));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getPolicyImport() {
        final var result = underTest.run(HttpRequest.GET("/" + IMPORTED_POLICY_ID));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImport.TYPE);
    }

    @Test
    public void putPolicyImport() {
        final var result = underTest.run(HttpRequest.PUT("/" + IMPORTED_POLICY_ID)
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_IMPORT_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyImport.TYPE);
    }

    @Test
    public void deletePolicyImport() {
        final var result = underTest.run(HttpRequest.DELETE("/" + IMPORTED_POLICY_ID));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), DeletePolicyImport.TYPE);
    }

    @Test
    public void getPolicyImportEntries() {
        final var result = underTest.run(HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/entries"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImportEntries.TYPE);
    }

    @Test
    public void putPolicyImportEntries() {
        final var result = underTest.run(HttpRequest.PUT("/" + IMPORTED_POLICY_ID + "/entries")
                .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRIES_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyImportEntries.TYPE);
    }

    @Test
    public void getPolicyImportEntriesAdditions() {
        final var result = underTest.run(
                HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/entriesAdditions"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImportEntriesAdditions.TYPE);
    }

    @Test
    public void putPolicyImportEntriesAdditions() {
        final var result = underTest.run(
                HttpRequest.PUT("/" + IMPORTED_POLICY_ID + "/entriesAdditions")
                        .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRIES_ADDITIONS_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyImportEntriesAdditions.TYPE);
    }

    @Test
    public void getPolicyImportEntryAddition() {
        final var result = underTest.run(
                HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/entriesAdditions/the_label"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImportEntryAddition.TYPE);
    }

    @Test
    public void putPolicyImportEntryAddition() {
        final var result = underTest.run(
                HttpRequest.PUT("/" + IMPORTED_POLICY_ID + "/entriesAdditions/the_label")
                        .withEntity(ContentTypes.APPLICATION_JSON, DUMMY_ENTRY_ADDITION_JSON));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), ModifyPolicyImportEntryAddition.TYPE);
    }

    @Test
    public void deletePolicyImportEntryAddition() {
        final var result = underTest.run(
                HttpRequest.DELETE("/" + IMPORTED_POLICY_ID + "/entriesAdditions/the_label"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), DeletePolicyImportEntryAddition.TYPE);
    }

    @Test
    public void getPolicyImportWithTrailingSlash() {
        final var result = underTest.run(HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImport.TYPE);
    }

    @Test
    public void getPolicyImportEntriesWithTrailingSlash() {
        final var result = underTest.run(HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/entries/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImportEntries.TYPE);
    }

    @Test
    public void getPolicyImportEntriesAdditionsWithTrailingSlash() {
        final var result = underTest.run(
                HttpRequest.GET("/" + IMPORTED_POLICY_ID + "/entriesAdditions/"));
        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(JsonKey.of("type"), RetrievePolicyImportEntriesAdditions.TYPE);
    }

}
