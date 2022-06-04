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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MissingThingIdsException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.junit.Before;
import org.junit.Test;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.scaladsl.model.HttpEntity;

/**
 * Tests {@link ThingsRoute}.
 */
public final class ThingsRouteTest extends EndpointTestBase {

    private ThingsRoute thingsRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        thingsRoute = new ThingsRoute(routeBaseProperties, messageConfig, claimMessageConfig);
        final Route route = extractRequestContext(ctx -> thingsRoute.buildThingsRoute(ctx, dittoHeaders));
        underTest = testRoute(handleExceptions(() -> route));
    }

    @Test
    public void putNewThingWithAttributesSuccessfully() {
        final var result = underTest.run(HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy")
                .withEntity(ContentTypes.APPLICATION_JSON, "{\"attributes\": {\"foo\": \"bar\"}}"));
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void postFeaturesReturnsMethodNotAllowed() {
        final var result = underTest.run(HttpRequest.POST("/things/org.eclipse.ditto%3Adummy/features"));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void createThingWithInvalidInitialPolicy() {
        final var body = "{\"_policy\"org.eclipse.ditto:1234}";
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body);
        final var result = underTest.run(HttpRequest.POST("/things").withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void createThingWithInvalidInitialDefinition() {
        final var body = "{\"definition\"org.eclipse.ditto:1234}";
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body);
        final var result = underTest.run(HttpRequest.POST("/things").withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void postThingWithLiveChannelQueryParameter() {
        final var thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributesBuilder().set("manufacturer", "ACME").build())
                .build();
        final var requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, thing.toJsonString());
        final var httpRequest = HttpRequest.POST("/things?channel=live").withEntity(requestEntity);
        final var thingNotCreatableException = ThingNotCreatableException.forLiveChannel(dittoHeaders);

        final var testRouteResult = underTest.run(httpRequest);

        testRouteResult.assertEntity(thingNotCreatableException.toJsonString());
    }

    @Test
    public void postThingWithLiveChannelHeader() {
        dittoHeaders = DittoHeaders.newBuilder(dittoHeaders).channel("live").build();

        final var thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributesBuilder().set("manufacturer", "ACME").build())
                .build();
        final var requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, thing.toJsonString());
        final var httpRequest = HttpRequest.POST("/things").withEntity(requestEntity);
        final var thingNotCreatableException = ThingNotCreatableException.forLiveChannel(dittoHeaders);

        final var testRouteResult = underTest.run(httpRequest);

        testRouteResult.assertEntity(thingNotCreatableException.toJsonString());
    }

    @Test
    public void putPolicyIdAssumesJsonContentType() {
        final var nonJsonStringResponse = underTest.run(HttpRequest.PUT("/things/" +
                        EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                .withEntity(ContentTypes.APPLICATION_JSON, "hello:world:123")).entityString();
        assertThat(JsonObject.of(nonJsonStringResponse)).contains(JsonKey.of("error"), "json.invalid");

        final var jsonStringResponse =
                underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                        .withEntity((RequestEntity) HttpEntity.apply("\"hello:world:123\"")
                                .withContentType(ContentTypes.APPLICATION_JSON))).entityString();
        assertThat(JsonObject.of(jsonStringResponse)).contains(JsonKey.of("type"), ModifyPolicyId.TYPE);
    }

    @Test
    public void putDefinitionAssumesJsonContentType() {
        final var nonJsonStringResponse = underTest.run(HttpRequest.PUT("/things/" +
                        EndpointTestConstants.KNOWN_THING_ID + "/definition")
                .withEntity(ContentTypes.APPLICATION_JSON, "hello:world:123")).entityString();
        assertThat(JsonObject.of(nonJsonStringResponse)).contains(JsonKey.of("error"), "json.invalid");

        final var jsonStringResponse =
                underTest.run(HttpRequest.PUT("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition")
                        .withEntity((RequestEntity) HttpEntity.apply("\"hello:world:123\"")
                                .withContentType(ContentTypes.APPLICATION_JSON))).entityString();
        assertThat(JsonObject.of(jsonStringResponse)).contains(JsonKey.of("type"), ModifyThingDefinition.TYPE);
    }

    @Test
    public void putAndRetrieveNullDefinition() {
        final var putResult = underTest.run(HttpRequest.PUT("/things/" +
                        EndpointTestConstants.KNOWN_THING_ID + "/definition")
                .withEntity(ContentTypes.APPLICATION_JSON, "null")).entityString();
        assertThat(JsonObject.of(putResult)).contains(JsonKey.of("type"), ModifyThingDefinition.TYPE);

        final var getResult = underTest.run(HttpRequest.GET("/things/" +
                EndpointTestConstants.KNOWN_THING_ID + "/definition"));
        getResult.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getThingsWithEmptyIdsList() {
        final var result = underTest.run(HttpRequest.GET("/things?ids="));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        final var expectedEx = MissingThingIdsException.newBuilder()
                .dittoHeaders(dittoHeaders)
                .build();
        result.assertEntity(expectedEx.toJsonString());
    }

    @Test
    public void getAttributesWithTrailingSlash() {
        final var request = HttpRequest.GET("/things/org.eclipse.ditto%3Adummy/attributes/");
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.OK);
        final var entityString = result.entityString();
        assertThat(entityString).contains(RetrieveAttributes.TYPE);
    }

    @Test
    public void getAttributesWithoutSlashButRandomText() {
        final var request = HttpRequest.GET("/things/org.eclipse.ditto%3Adummy/attributesasfsafa");
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putAttributeWithEmptyPointer() {
        final var body = "\"bumlux\"";
        final var request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes//bar")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, body));
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void putAttributeWithJsonException() {
        final var tooLongNumber = "89314404000484999942";
        final var request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes/attribute")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, tooLongNumber));
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void putAttributeWithJsonPointerException() {
        final var attributeJson = "{\"/attributeTest\":\"test\"}";
        final var request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, attributeJson));
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchThingWithAttributesSuccessfully() {
        final var result = underTest.run(HttpRequest.PATCH("/things/org.eclipse.ditto%3Adummy/attributes")
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), "{\"foo\": \"bar\"}"));
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void patchThingWithAttributesWithJsonException() {
        final var result = underTest.run(HttpRequest.PATCH("/things/org.eclipse.ditto%3Adummy/attributes")
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), "{\"foo\", \"bar\"}"));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchThingWithAttributeSuccessfully() {
        final var body = "\"bumlux\"";
        final var request = HttpRequest.PATCH("/things/org.eclipse.ditto%3Adummy/attributes/bar")
                .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), body));
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void patchThingWithAttributeWithJsonException() {
        final var tooLongNumber = "89314404000484999942";
        final var request = HttpRequest.PATCH("/things/org.eclipse.ditto%3Adummy/attributes/bar")
                .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                        tooLongNumber));
        final var result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchDefinitionSuccessfully() {
        final var result = underTest.run(HttpRequest.PATCH("/things/" + EndpointTestConstants.KNOWN_THING_ID
                        + "/definition")
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), "\"hello:world:123\""));
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void patchDefinitionWithJsonException() {
        final var result =
                underTest.run(HttpRequest.PATCH("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/definition")
                        .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), "hello:world:123"));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchPolicyIdAssumesJsonContentType() {
        final var nonJsonStringResponse =
                underTest.run(HttpRequest.PATCH("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), "hello:world:123"))
                        .entityString();
        assertThat(JsonObject.of(nonJsonStringResponse)).contains(JsonKey.of("error"), "json.invalid");

        final var jsonStringResponse =
                underTest.run(HttpRequest.PATCH("/things/" + EndpointTestConstants.KNOWN_THING_ID + "/policyId")
                                .withEntity((RequestEntity) HttpEntity.apply("\"hello:world:123\"")
                                        .withContentType(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType())))
                        .entityString();
        assertThat(JsonObject.of(jsonStringResponse)).contains(JsonKey.of("type"), MergeThing.TYPE);
    }

}
