/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_SUBJECT;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_THING_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.time.Duration;

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
import org.junit.Before;
import org.junit.Test;

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

    private FeaturesRoute featuresRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        featuresRoute = new FeaturesRoute(createDummyResponseActor(), system(), Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO);
        final Route route =
                extractRequestContext(ctx -> featuresRoute.buildFeaturesRoute(ctx, DittoHeaders.newBuilder().build(),
                        KNOWN_THING_ID));
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

}
