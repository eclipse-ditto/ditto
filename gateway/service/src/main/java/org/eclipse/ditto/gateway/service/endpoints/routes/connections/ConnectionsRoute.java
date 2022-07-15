/*
 * Copyright Bosch Software Innovations GmbH 2017
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch Software Innovations GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch Software Innovations GmbH.
 */
package org.eclipse.ditto.gateway.service.endpoints.routes.connections;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.gateway.service.endpoints.directives.ContentTypeValidationDirective.ensureValidContentType;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionIdNotExplicitlySettableException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectivityCommandInvalidException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /connections}.
 */
public final class ConnectionsRoute extends AbstractRoute {

    /**
     * Path segment of the "connections" URI.
     */
    public static final String PATH_CONNECTIONS = "connections";

    private static final String PATH_COMMAND = "command";
    private static final String PATH_METRICS = "metrics";
    private static final String PATH_STATUS = "status";
    private static final String PATH_LOGS = "logs";

    private final Set<String> mediaTypePlainTextWithFallbacks;

    /**
     * Constructs a {@code ConnectionsRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public ConnectionsRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);

        final var httpConfig = routeBaseProperties.getHttpConfig();
        final var fallbackMediaTypes = httpConfig.getAdditionalAcceptedMediaTypes().stream();
        final var plainText = Stream.of(MediaTypes.TEXT_PLAIN.toString());
        mediaTypePlainTextWithFallbacks = Stream.concat(plainText, fallbackMediaTypes).collect(Collectors.toSet());
    }

    /**
     * Builds the {@code /connections} route.
     *
     * @param dittoHeaders the headers of the request.
     * @return the {@code /connections} route.
     */
    public Route buildConnectionsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_CONNECTIONS), () ->
                        concat(
                                // /connections
                                connections(ctx, dittoHeaders),
                                rawPathPrefix(
                                        PathMatchers.slash().concat(PathMatchers.segment()),
                                        connectionId -> connectionRoute(ctx, dittoHeaders,
                                                ConnectionId.of(connectionId))
                                )
                        )
        );
    }

    private Route connectionRoute(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return concat(
                connectionsEntry(ctx, dittoHeaders, connectionId),
                connectionsEntryCommand(ctx, dittoHeaders, connectionId),
                connectionsEntryStatus(ctx, dittoHeaders, connectionId),
                connectionsEntryMetrics(ctx, dittoHeaders, connectionId),
                connectionsEntryLogs(ctx, dittoHeaders, connectionId)
        );
    }

    /*
     * Describes {@code /connections} route.
     *
     * @return {@code /connections} route.
     */
    private Route connections(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /connections
                                handlePerRequest(ctx,
                                        RetrieveConnections.newInstance(Collections.emptySet(), dittoHeaders)
                                )
                        ),
                        post(() -> // POST /connections?dry-run=<dryRun>
                                parameterOptional(ConnectionsParameter.DRY_RUN.toString(), dryRun ->
                                        ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                dittoHeaders,
                                                payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                connectionJsonString ->
                                                                        handlePostConnection(dryRun,
                                                                                connectionJsonString, dittoHeaders)
                                                        )
                                        )
                                )
                        )
                )
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Command<?> handlePostConnection(final Optional<String> dryRun,
            final String connectionJsonString,
            final DittoHeaders dittoHeaders) {

        if (isDryRun(dryRun)) {
            return TestConnection.of(buildConnectionForTest(connectionJsonString),
                            dittoHeaders.toBuilder().putHeader("timeout", "15000").build());
        }
        return CreateConnection.of(buildConnectionForPost(connectionJsonString), dittoHeaders);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static boolean isDryRun(final Optional<String> dryRun) {
        return dryRun.map(Boolean::valueOf).orElse(Boolean.FALSE);
    }

    /*
     * Describes {@code /connections/<connectionId>} route.
     *
     * @return {@code /connections/<connectionId>} route.
     */
    private Route connectionsEntry(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return pathEndOrSingleSlash(() ->
                concat(
                        put(() -> // PUT /connections/<connectionId>
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource, payloadJsonString ->
                                                                ModifyConnection.of(
                                                                        buildConnectionForPut(connectionId,
                                                                                payloadJsonString),
                                                                        dittoHeaders))
                                )
                        ),
                        get(() -> // GET /connections/<connectionId>
                                handlePerRequest(ctx, RetrieveConnection.of(connectionId, dittoHeaders))
                        ),
                        delete(() -> // DELETE /connections/<connectionId>
                                handlePerRequest(ctx, DeleteConnection.of(connectionId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /connections/<connectionId>/command} route.
     *
     * @return {@code /connections/<connectionId>/command} route.
     */
    private Route connectionsEntryCommand(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return path(PATH_COMMAND, () ->
                concat(
                        post(() -> // POST /connections/<connectionId>/command
                                ensureValidContentType(mediaTypePlainTextWithFallbacks, ctx, dittoHeaders,
                                        () -> extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        payloadJsonString -> buildConnectivityCommand(
                                                                payloadJsonString,
                                                                connectionId, dittoHeaders)))
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /connections/<connectionId>/status} route.
     *
     * @return {@code /connections/<connectionId>/status} route.
     */
    private Route connectionsEntryStatus(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return path(PATH_STATUS, () ->
                concat(
                        get(() -> // GET /connections/<connectionId>/status
                                handlePerRequest(ctx, RetrieveConnectionStatus.of(connectionId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /connections/<connectionId>/metrics} route.
     *
     * @return {@code /connections/<connectionId>/metrics} route.
     */
    private Route connectionsEntryMetrics(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return path(PATH_METRICS, () ->
                concat(
                        get(() -> // GET /connections/<connectionId>/metrics
                                handlePerRequest(ctx, RetrieveConnectionMetrics.of(connectionId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /connections/<connectionId>/logs} route.
     *
     * @return {@code /connections/<connectionId>/logs} route.
     */
    private Route connectionsEntryLogs(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ConnectionId connectionId) {

        return path(PATH_LOGS, () ->
                get(() -> // GET /connections/<connectionId>/logs
                        handlePerRequest(ctx, RetrieveConnectionLogs.of(connectionId, dittoHeaders))
                )
        );
    }

    private static ConnectivityCommand<?> buildConnectivityCommand(final String commandString,
            final ConnectionId connectionId,
            final DittoHeaders dittoHeaders) {

        return switch (commandString) {
            case OpenConnection.TYPE -> OpenConnection.of(connectionId, dittoHeaders);
            case CloseConnection.TYPE -> CloseConnection.of(connectionId, dittoHeaders);
            case ResetConnectionMetrics.TYPE -> ResetConnectionMetrics.of(connectionId, dittoHeaders);
            case EnableConnectionLogs.TYPE -> EnableConnectionLogs.of(connectionId, dittoHeaders);
            case ResetConnectionLogs.TYPE -> ResetConnectionLogs.of(connectionId, dittoHeaders);
            default -> throw ConnectivityCommandInvalidException.newBuilder(commandString)
                    .dittoHeaders(dittoHeaders)
                    .build();
        };
    }

    private static Connection buildConnectionForPost(final String connectionJson) {
        final JsonObject connectionJsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(connectionJson));

        if (connectionJsonObject.contains(Connection.JsonFields.ID.getPointer())) {
            throw ConnectionIdNotExplicitlySettableException.newBuilder().build();
        }

        final JsonObjectBuilder jsonObjectBuilder = connectionJsonObject.toBuilder();
        jsonObjectBuilder.set(Connection.JsonFields.ID, UUID.randomUUID().toString());
        final String connectionStatus = connectionJsonObject.getValue(Connection.JsonFields.CONNECTION_STATUS)
                .orElse(ConnectivityStatus.UNKNOWN.getName());
        jsonObjectBuilder.set(Connection.JsonFields.CONNECTION_STATUS, connectionStatus);

        return ConnectivityModelFactory.connectionFromJson(jsonObjectBuilder.build());
    }

    private static Connection buildConnectionForTest(final String connectionJson) {
        final JsonObject connectionJsonObject;
        final JsonObject connectionJsonObjectBeforeReplacement =
                wrapJsonRuntimeException(() -> JsonFactory.newObject(connectionJson));
        final Optional<String> optionalConnectionId =
                connectionJsonObjectBeforeReplacement.getValue(Connection.JsonFields.ID);
        final String temporaryTestConnectionId = UUID.randomUUID() + "-dry-run";
        if (optionalConnectionId.isPresent()) {
            final String temporaryConnectionJson =
                    connectionJson.replace(optionalConnectionId.get(), temporaryTestConnectionId);
            connectionJsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(temporaryConnectionJson));
        } else {
            final JsonObjectBuilder jsonObjectBuilder = connectionJsonObjectBeforeReplacement.toBuilder();
            connectionJsonObject = jsonObjectBuilder.set(Connection.JsonFields.ID, temporaryTestConnectionId).build();
        }
        return ConnectivityModelFactory.connectionFromJson(connectionJsonObject);
    }

    private static Connection buildConnectionForPut(final ConnectionId connectionId, final String connectionJson) {
        final JsonObject connectionJsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(connectionJson));
        if (connectionJsonObject.contains(Connection.JsonFields.ID.getPointer())
                &&
                !connectionId.toString().equals(connectionJsonObject.getValue(Connection.JsonFields.ID).orElse(null))) {
            throw ConnectionIdNotExplicitlySettableException.newBuilder().build();
        }

        final JsonObjectBuilder jsonObjectBuilder = connectionJsonObject.toBuilder();
        jsonObjectBuilder.set(Connection.JsonFields.ID, connectionId.toString());

        return ConnectivityModelFactory.connectionFromJson(jsonObjectBuilder.build());
    }

}
