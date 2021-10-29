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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_SUBJECT;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_THING_ID;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.junit.Before;
import org.junit.Test;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link FeaturesRoute}.
 */
public final class FeaturesRouteTest extends EndpointTestBase {

    private static final String FEATURES_PATH = "/" + FeaturesRoute.PATH_FEATURES;

    private static final String FEATURE_ENTRY_PATH = FEATURES_PATH + "/" + EndpointTestConstants.KNOWN_FEATURE_ID;

    private static final String FEATURE_ENTRY_DEFINITION_PATH =
            FEATURE_ENTRY_PATH + "/" + FeaturesRoute.PATH_DEFINITION;

    private static final String FEATURE_ENTRY_PROPERTIES_PATH =
            FEATURE_ENTRY_PATH + "/" + FeaturesRoute.PATH_PROPERTIES;

    private static final String FEATURE_ENTRY_DESIRED_PROPERTIES_PATH =
            FEATURE_ENTRY_PATH + "/" + FeaturesRoute.PATH_DESIRED_PROPERTIES;

    private static final String FEATURE_ENTRY_PROPERTIES_ENTRY_PATH =
            FEATURE_ENTRY_PROPERTIES_PATH + "/" + "knownJsonPointer";

    private static final String FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH =
            FEATURE_ENTRY_DESIRED_PROPERTIES_PATH + "/" + "knownJsonPointer";

    private static final String FEATURE_ENTRY_INBOX_MESSAGES_PATH =
            FEATURE_ENTRY_PATH + "/" + MessagesRoute.PATH_INBOX + "/" + MessagesRoute.PATH_MESSAGES;

    private TestRoute underTest;

    @Before
    public void setUp() {
        final var featuresRoute = new FeaturesRoute(routeBaseProperties,
                messageConfig,
                claimMessageConfig);
        underTest = getTestRoute(featuresRoute, dittoHeaders);
    }

    private TestRoute getTestRoute(final FeaturesRoute featuresRoute, final DittoHeaders dittoHeaders) {
        return testRoute(extractRequestContext(ctx -> featuresRoute.buildFeaturesRoute(ctx,
                dittoHeaders,
                KNOWN_THING_ID)));
    }

    @Test
    public void getFeatures() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeaturesWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURES_PATH + "?" + ThingsParameter.FIELDS
                .toString() + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatures() {
        final Features features = ThingsModelFactory.emptyFeatures();
        final TestRouteResult result = underTest.run(HttpRequest.PUT(FEATURES_PATH)
                .withEntity(ContentTypes.APPLICATION_JSON, features.toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void deleteFeatures() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeaturesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURES_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeatureEntryWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_PATH + "?" + ThingsParameter
                .FIELDS.toString() + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntry() {
        final Feature feature = ThingsModelFactory.newFeature("newFeatureId");
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PATH)
                        .withEntity(ContentTypes.APPLICATION_JSON, feature.toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void deleteFeatureEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntryDefinition() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_DEFINITION_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryDefinition() {
        final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier("org.eclipse.ditto:example:0.1.0");
        final TestRouteResult result = underTest.run(
                HttpRequest.PUT(FEATURE_ENTRY_DEFINITION_PATH)
                        .withEntity(ContentTypes.APPLICATION_JSON, featureDefinition.toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void deleteFeatureEntryDefinition() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_DEFINITION_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryDefinitionReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_DEFINITION_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntryProperties() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeatureEntryPropertiesWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result =
                underTest.run(HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_PATH + "?" + ThingsParameter.FIELDS.toString()
                        + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryProperties() {
        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder().build();
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_PATH)
                        .withEntity(ContentTypes.APPLICATION_JSON, featureProperties
                                .toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void deleteFeatureEntryProperties() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_PROPERTIES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryPropertiesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_PROPERTIES_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntryPropertiesEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeatureEntryPropertiesEntryWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result =
                underTest.run(HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH + "?" + ThingsParameter.FIELDS
                        .toString() + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryPropertiesEntry() {
        final String featurePropertiesEntry = "\"notImportantHere\"";
        final TestRouteResult result = underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (ContentTypes.APPLICATION_JSON, featurePropertiesEntry));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryPropertiesEntryWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final TestRouteResult result = underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (ContentTypes.APPLICATION_JSON, tooLongNumber));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void deleteFeatureEntryPropertiesEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryPropertiesEntryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntryDesiredProperties() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_DESIRED_PROPERTIES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeatureEntryDesiredPropertiesWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result =
                underTest.run(
                        HttpRequest.GET(FEATURE_ENTRY_DESIRED_PROPERTIES_PATH + "?" + ThingsParameter.FIELDS.toString()
                                + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryDesiredProperties() {
        final FeatureProperties featureDesiredProperties = ThingsModelFactory.newFeaturePropertiesBuilder().build();
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_DESIRED_PROPERTIES_PATH)
                        .withEntity(ContentTypes.APPLICATION_JSON, featureDesiredProperties
                                .toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void deleteFeatureEntryDesiredProperties() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_DESIRED_PROPERTIES_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryDesiredPropertiesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_DESIRED_PROPERTIES_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getFeatureEntryDesiredPropertiesEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getFeatureEntryDesiredPropertiesEntryWithFieldSelector() {
        final String someField = "featureId";
        final TestRouteResult result =
                underTest.run(HttpRequest.GET(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH + "?" + ThingsParameter.FIELDS
                        .toString() + "=" + someField));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryDesiredPropertiesEntry() {
        final String featureDesiredPropertiesEntry = "\"notImportantHere\"";
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH).withEntity
                        (ContentTypes.APPLICATION_JSON, featureDesiredPropertiesEntry));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryDesiredPropertiesEntryWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH).withEntity
                        (ContentTypes.APPLICATION_JSON, tooLongNumber));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void deleteFeatureEntryDesiredPropertiesEntry() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postFeatureEntryDesiredPropertiesEntryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postFeatureEntryInboxMessage() {
        final var proxyActor = startEchoActor(
                SendFeatureMessage.class,
                sendFeatureMessage -> SendFeatureMessageResponse.of(sendFeatureMessage.getEntityId(),
                        sendFeatureMessage.getFeatureId(),
                        sendFeatureMessage.getMessage(),
                        HttpStatus.OK,
                        sendFeatureMessage.getDittoHeaders())
        );

        final var routeBaseProperties = RouteBaseProperties.newBuilder(this.routeBaseProperties)
                .proxyActor(proxyActor)
                .build();

        underTest =
                getTestRoute(new FeaturesRoute(routeBaseProperties, messageConfig, claimMessageConfig), dittoHeaders);

        final var result = underTest.run(
                HttpRequest.create()
                        .withMethod(HttpMethods.POST)
                        .withUri(FEATURE_ENTRY_INBOX_MESSAGES_PATH + "/" + KNOWN_SUBJECT)
                        .withEntity("messageContent")
        );

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getNonExistingSubUrl() {
        final TestRouteResult result = underTest.run(HttpRequest.GET(FEATURE_ENTRY_PATH + UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putPropertyWithJsonPointerException() {
        final String featureJson = "{\"/wrongProperty\":\"value\"}";
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_PATH)
                        .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, featureJson)));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getPropertiesWithTrailingSlash() {
        final HttpRequest request = HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_PATH + "/");
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
        final String entityString = result.entityString();
        assertThat(entityString).contains(RetrieveFeatureProperties.TYPE);
    }

    @Test
    public void getPropertiesWithoutSlashButOtherText() {
        final HttpRequest request = HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_PATH + "sfsdgsdg");
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putPropertyWithEmptyPointer() {
        final HttpRequest request = HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_PATH + "//bar")
                .withEntity(ContentTypes.APPLICATION_JSON, "\"bumlux\"");
        final TestRouteResult result = underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchFeatureEntrySuccessfully() {
        final Feature feature = ThingsModelFactory.newFeature("newFeatureId");
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PATH)
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), feature.toJsonString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchFeatureEntryWithJsonException() {
        final String featureNotParsable = "{\"water-tank\":{\"properties\": {}}";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PATH).withEntity
                (MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), featureNotParsable));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchFeaturePropertiesEntrySuccessfully() {
        final String featurePropertiesEntry = "\"notImportantHere\"";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), featurePropertiesEntry));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchFeaturePropertiesEntryWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), tooLongNumber));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchPropertiesSuccessfully() {
        final String featurePropertiesJson = "{\"property\":\"value1\"}";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_PATH)
                .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                        featurePropertiesJson)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchPropertiesWithJsonPointerException() {
        final String featurePropertiesJson = "{\"/wrongProperty\":\"withMissingClosingCurlyBracket\"";
        final TestRouteResult result =
                underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_PATH)
                        .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                                featurePropertiesJson)));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchPropertySuccessfully() {
        final String featurePropertyJson = "\"value1\"";
        final TestRouteResult result =
                underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH)
                        .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                                featurePropertyJson)));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchPropertyWithJsonPointerException() {
        final String featureJson = "{\"/wrongProperty\":\"withMissingClosingCurlyBracket\"";
        final TestRouteResult result =
                underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH)
                        .withEntity(HttpEntities.create(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                                featureJson)));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchFeatureEntryDesiredPropertiesEntrySuccessfully() {
        final String featureDesiredPropertiesEntry = "{\"desiredProperty\":\"value0815\"}";
        final TestRouteResult result =
                underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH)
                        .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                                featureDesiredPropertiesEntry));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchFeatureEntryDesiredPropertiesEntryWithJsonException() {
        final String nestedFeatureDesiredPropertiesEntry =
                "{\"nestedDesiredProperty\": {\"desiredProperty\":\"value0815\"}";
        final TestRouteResult result =
                underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_DESIRED_PROPERTIES_ENTRY_PATH)
                        .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(),
                                nestedFeatureDesiredPropertiesEntry));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void patchFeatureEntryDefinitionSuccessfully() {
        final String featureDefinition = "[\"org.eclipse.ditto:example:0.1.0\"]";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_DEFINITION_PATH)
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), featureDefinition));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void patchFeatureEntryDefinitionWithJsonException() {
        final String featureDefinition = "org.eclipse.ditto:example:0.1.0";
        final TestRouteResult result = underTest.run(HttpRequest.PATCH(FEATURE_ENTRY_DEFINITION_PATH)
                .withEntity(MediaTypes.APPLICATION_MERGE_PATCH_JSON.toContentType(), featureDefinition));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

}
