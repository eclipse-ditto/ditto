/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.connections;

import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newSourceBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.UnsupportedMediaTypeException;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;

public final class ConnectionsRouteTest extends EndpointTestBase {

    private static final JsonValue DEFAULT_DUMMY_ENTITY_JSON = JsonValue.of("dummy");
    private static final String DEFAULT_DUMMY_ENTITY = DEFAULT_DUMMY_ENTITY_JSON.toString();

    private static final Function<Jsonifiable<?>, Optional<Object>> DUMMY_RESPONSE_PROVIDER =
            m -> DummyThingModifyCommandResponse.echo(null);
    private static final String SUBJECT_ID =  "authorization:subject";
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION, AuthorizationSubject.newInstance(SUBJECT_ID));

    private static final List<Source> SOURCES = Arrays.asList(
            newSourceBuilder().authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source1")
                    .consumerCount(2)
                    .index(0)
                    .build(),
            newSourceBuilder().authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source1")
                    .consumerCount(2)
                    .index(0)
                    .build());

    private static final List<Target> TARGETS = Collections.singletonList(ConnectivityModelFactory.newTargetBuilder()
            .address("twinEventExchange/twinEventRoutingKey")
            .authorizationContext(AUTHORIZATION_CONTEXT)
            .topics(Topic.TWIN_EVENTS)
            .build());

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    private static final ConnectionType CONNECTION_TYPE = ConnectionType.AMQP_10;
    private static final ConnectivityStatus CONNECTION_STATUS = ConnectivityStatus.OPEN;

    private static final JsonObject JSON_CONNECTION_WITH_ID = createConnectionJson(CONNECTION_ID);
    private static final JsonObject JSON_CONNECTION_WITH_WRONG_ID =
            createConnectionJson(ConnectionId.of("other-uuid"));
    private static final JsonObject JSON_CONNECTION_WITHOUT_ID =
            createConnectionJson(CONNECTION_ID).remove(Connection.JsonFields.ID.getPointer().toString());

    private TestRoute underTest;

    @Before
    public void setUp() {
        final DevopsAuthenticationDirective devopsAuthenticationDirective = Mockito.mock(
                DevopsAuthenticationDirective.class);
        Mockito.when(devopsAuthenticationDirective.authenticateDevOps(Mockito.any(), Mockito.any()))
                .thenAnswer(a -> a.getArguments()[1]);
        final var connectionsRoute = new ConnectionsRoute(routeBaseProperties, devopsAuthenticationDirective);
        final Route route =
                extractRequestContext(ctx -> connectionsRoute.buildConnectionsRoute(ctx, dittoHeaders));
        underTest = testRoute(route);
    }

    @Override
    protected Function<Jsonifiable<?>, Optional<Object>> getResponseProvider() {
        return DUMMY_RESPONSE_PROVIDER;
    }

    @Test
    public void testPostConnection() {
        underTest.run(HttpRequest.POST("/connections/")
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITHOUT_ID.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionWithIdFails() {
        underTest.run(HttpRequest.POST("/connections/")
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITH_ID.toString()))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void testPostConnectionWithDryRun() {
        underTest.run(HttpRequest.POST("/connections?dry-run=true")
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITHOUT_ID.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testRetrieveConnection() {
        underTest.run(HttpRequest.GET("/connections/" + CONNECTION_ID))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testRetrieveConnections() {
        underTest.run(HttpRequest.GET("/connections"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPutConnection() {
        underTest.run(HttpRequest.PUT("/connections/" + CONNECTION_ID)
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITH_ID.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPutConnectionWithoutId() {
        underTest.run(HttpRequest.PUT("/connections/" + CONNECTION_ID)
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITHOUT_ID.toString()))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPutConnectionWithWrongIdFails() {
        underTest.run(HttpRequest.PUT("/connections/" + CONNECTION_ID)
                        .withEntity(ContentTypes.APPLICATION_JSON, JSON_CONNECTION_WITH_WRONG_ID.toString()))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void testDeleteConnection() {
        underTest.run(HttpRequest.DELETE("/connections/" + CONNECTION_ID))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionCommandOpen() {
        underTest.run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8,
                                "connectivity.commands:openConnection"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionCommandClose() {
        underTest.run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8,
                                "connectivity.commands:closeConnection"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionCommandResetMetrics() {
        underTest.run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8,
                                "connectivity.commands:resetConnectionMetrics"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionEnableConnectionLogs() {
        underTest.run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8,
                                "connectivity.commands:enableConnectionLogs"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionResetConnectionLogs() {
        underTest.run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8,
                                "connectivity.commands:resetConnectionLogs"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testPostConnectionCommandWrongFails() {
        final var routeWithExceptionHandler = underTest.underlying()
                .seal(RejectionHandler.defaultHandler(), ExceptionHandler.newBuilder()
                        .match(UnsupportedMediaTypeException.class,
                                x -> Directives.complete(StatusCodes.UNSUPPORTED_MEDIA_TYPE))
                        .build());
        testRoute(routeWithExceptionHandler).run(HttpRequest.POST("/connections/" + CONNECTION_ID + "/command")
                        .withEntity(ContentTypes.APPLICATION_JSON, "\"other\""))
                .assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testGetConnectionMetrics() {
        underTest.run(HttpRequest.GET("/connections/" + CONNECTION_ID + "/metrics"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testGetConnectionStatus() {
        underTest.run(HttpRequest.GET("/connections/" + CONNECTION_ID + "/status"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void testGetConnectionLogs() {
        underTest.run(HttpRequest.GET("/connections/" + CONNECTION_ID + "/logs"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    private static JsonObject createConnectionJson(final ConnectionId connectionId) {
        return ConnectivityModelFactory.newConnectionBuilder(
                        connectionId, CONNECTION_TYPE, CONNECTION_STATUS, "amqps://user:pw@localhost:443")
                .sources(SOURCES)
                .targets(TARGETS)
                .build()
                .toJson();
    }

}
