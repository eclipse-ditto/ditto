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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_SUBJECT;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_THING_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link FeaturesRoute}.
 */
public final class FeaturesRouteTest extends EndpointTestBase {

    private static final String FEATURES_PATH = "/" + FeaturesRoute.PATH_PREFIX;
    private static final String FEATURE_ENTRY_PATH = FEATURES_PATH + "/" + EndpointTestConstants.KNOWN_FEATURE_ID;
    private static final String FEATURE_ENTRY_DEFINITION_PATH =
            FEATURE_ENTRY_PATH + "/" + FeaturesRoute.PATH_DEFINITION;
    private static final String FEATURE_ENTRY_PROPERTIES_PATH =
            FEATURE_ENTRY_PATH + "/" + FeaturesRoute.PATH_PROPERTIES;
    private static final String FEATURE_ENTRY_PROPERTIES_ENTRY_PATH = FEATURE_ENTRY_PROPERTIES_PATH + "/" +
            "knownJsonPointer";
    private static final String FEATURE_ENTRY_INBOX_MESSAGES_PATH = FEATURE_ENTRY_PATH + "/" +
            MessagesRoute.PATH_INBOX + "/" + MessagesRoute.PATH_MESSAGES;

    @Rule
    public final TestName testName = new TestName();

    private FeaturesRoute featuresRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName())
                .build();

        featuresRoute = new FeaturesRoute(createDummyResponseActor(), actorSystem, httpConfig, commandConfig,
                messageConfig, claimMessageConfig, adapterProvider.getHttpHeaderTranslator());
        final Route route = extractRequestContext(
                ctx -> featuresRoute.buildFeaturesRoute(ctx, dittoHeaders, KNOWN_THING_ID));
        underTest = testRoute(route);
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
        final TestRouteResult result =
                underTest.run(HttpRequest.PUT(FEATURES_PATH).withEntity(features.toJsonString()));
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
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PATH).withEntity(feature.toJsonString()));
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
        final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier("org.eclipse.ditto:vorto:0.1.0");
        final TestRouteResult result = underTest.run(
                HttpRequest.PUT(FEATURE_ENTRY_DEFINITION_PATH).withEntity(featureDefinition.toJsonString()));
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
                underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_PATH).withEntity(featureProperties
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
        final JsonValue featurePropertiesEntry = JsonFactory.readFrom("\"notImportantHere\"");
        final TestRouteResult result = underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (featurePropertiesEntry.toString()));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void putFeatureEntryPropertiesEntryWithJsonException() {
        final String tooLongNumber = "89314404000484999942";
        final TestRouteResult result = underTest.run(HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_ENTRY_PATH).withEntity
                (tooLongNumber));
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
    public void postFeatureEntryInboxMessage() {
        final String messageContent = "messageContent";
        final TestRouteResult result = underTest.run(HttpRequest.POST(FEATURE_ENTRY_INBOX_MESSAGES_PATH + "/" +
                KNOWN_SUBJECT)
                .withEntity(messageContent));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
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
        final TestRouteResult result =
                underTest.run(request);
        result.assertStatusCode(StatusCodes.OK);
        final String entityString = result.entityString();
        assertThat(entityString).contains(RetrieveFeatureProperties.TYPE);
    }

    @Test
    public void getPropertiesWithoutSlashButOtherText() {
        final HttpRequest request = HttpRequest.GET(FEATURE_ENTRY_PROPERTIES_PATH + "sfsdgsdg");
        final TestRouteResult result =
                underTest.run(request);
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putPropertyWithEmptyPointer() {
        final HttpRequest request = HttpRequest.PUT(FEATURE_ENTRY_PROPERTIES_PATH + "//bar")
                .withEntity("\"bumlux\"");
        final TestRouteResult result =
                underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

}
