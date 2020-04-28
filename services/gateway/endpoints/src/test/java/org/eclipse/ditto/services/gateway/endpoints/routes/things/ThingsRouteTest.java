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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.things.exceptions.MissingThingIdsException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.http.scaladsl.model.HttpEntity;

/**
 * Tests {@link ThingsRoute}.
 */
public final class ThingsRouteTest extends EndpointTestBase {

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private ThingsRoute thingsRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        thingsRoute = new ThingsRoute(createDummyResponseActor(), actorSystem, httpConfig, commandConfig, messageConfig,
                claimMessageConfig, adapterProvider.getHttpHeaderTranslator());

        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        final Route route = extractRequestContext(ctx -> thingsRoute.buildThingsRoute(ctx, dittoHeaders));
        underTest = testRoute(route);
    }

    @Test
    public void postFeaturesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things/org.eclipse.ditto%3Adummy/features"));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void createThingWithInvalidInitialPolicy() {
        final String body = "{\"_policy\"org.eclipse.ditto:1234}";
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body);
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things").withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void createThingWithInvalidInitialDefinition() {
        final String body = "{\"definition\"org.eclipse.ditto:1234}";
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body);
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things").withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void putPolicyIdAssumesJsonContentType() {
        final String nonJsonStringResponse = underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                .withEntity("hello:world:123")).entityString();
        assertThat(JsonObject.of(nonJsonStringResponse)).contains(JsonKey.of("error"), "json.invalid");

        final String jsonStringResponse = underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                .withEntity((RequestEntity) HttpEntity.apply("\"hello:world:123\"")
                        .withContentType(ContentTypes.APPLICATION_JSON)))
                .entityString();
        assertThat(JsonObject.of(jsonStringResponse)).contains(JsonKey.of("type"), ModifyPolicyId.TYPE);
    }

    @Test
    public void putDefinitionAssumesJsonContentType() {
        final String nonJsonStringResponse = underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition")
                .withEntity("hello:world:123")).entityString();
        assertThat(JsonObject.of(nonJsonStringResponse)).contains(JsonKey.of("error"), "json.invalid");

        final String jsonStringResponse = underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition")
                .withEntity((RequestEntity) HttpEntity.apply("\"hello:world:123\"")
                        .withContentType(ContentTypes.APPLICATION_JSON)))
                .entityString();
        assertThat(JsonObject.of(jsonStringResponse)).contains(JsonKey.of("type"), ModifyThingDefinition.TYPE);
    }

    @Test
    public void putAndRetrieveNullDefinition() {
        final String putResult = underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition")
                .withEntity("null")).entityString();
        assertThat(JsonObject.of(putResult)).contains(JsonKey.of("type"), ModifyThingDefinition.TYPE);

        final TestRouteResult getResult = underTest.run(HttpRequest.GET("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition"));
        getResult.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getThingsWithEmptyIdsList() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/things?ids="));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        final MissingThingIdsException expectedEx = MissingThingIdsException.newBuilder()
                .dittoHeaders(dittoHeaders)
                .build();
        result.assertEntity(expectedEx.toJsonString());
    }

    @Test
    public void getAttributesWithTrailingSlash() {
        final HttpRequest request = HttpRequest.GET("/things/org.eclipse.ditto%3Adummy/attributes/");
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.OK);
        final String entityString = result.entityString();
        assertThat(entityString).contains(RetrieveAttributes.TYPE);
    }

    @Test
    public void getAttributesWithoutSlashButRandomText() {
        final HttpRequest request = HttpRequest.GET("/things/org.eclipse.ditto%3Adummy/attributesasfsafa");
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putAttributeWithEmptyPointer() {
        final String body = "\"bumlux\"";
        final HttpRequest request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes//bar")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, body));
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void putAttributeWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final HttpRequest request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes/attribute")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, tooLongNumber));
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void putAttributeWithJsonPointerException() {
        final String attributeJson = "{\"/attributeTest\":\"test\"}";
        final HttpRequest request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, attributeJson));
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

}
