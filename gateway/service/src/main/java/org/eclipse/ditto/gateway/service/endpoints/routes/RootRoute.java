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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.gateway.service.endpoints.directives.CorrelationIdEnsuringDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.CorsEnablingDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.EncodingEnsuringDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.RequestResultLoggingDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.RequestTimeoutHandlingDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.RequestTracingDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.routes.cloudevents.CloudEventsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.connections.ConnectionsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiRoute;
import org.eclipse.ditto.gateway.service.endpoints.utils.DittoRejectionHandlerFactory;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.health.routes.StatusRoute;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.RouteAdapter;
import akka.http.scaladsl.server.RouteResult;
import akka.japi.pf.PFBuilder;
import scala.Function1;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

/**
 * Builder for creating Akka HTTP routes for {@code /}.
 */
public final class RootRoute extends AllDirectives {

    static final String HTTP_PATH_API_PREFIX = "api";
    static final String WS_PATH_PREFIX = "ws";

    private final StatusRoute ownStatusRoute;
    private final OverallStatusRoute overallStatusRoute;
    private final CachingHealthRoute cachingHealthRoute;
    private final DevOpsRoute devopsRoute;

    private final PoliciesRoute policiesRoute;
    private final SseRouteBuilder sseThingsRouteBuilder;
    private final ThingsRoute thingsRoute;
    private final ThingSearchRoute thingSearchRoute;
    private final ConnectionsRoute connectionsRoute;
    private final WebSocketRouteBuilder websocketRouteBuilder;
    private final StatsRoute statsRoute;
    private final WhoamiRoute whoamiRoute;
    private final CloudEventsRoute cloudEventsRoute;

    private final CustomApiRoutesProvider customApiRoutesProvider;
    private final RouteBaseProperties routeBaseProperties;
    private final GatewayAuthenticationDirective apiAuthenticationDirective;
    private final GatewayAuthenticationDirective wsAuthenticationDirective;
    private final CorsEnablingDirective corsDirective;
    private final HttpsEnsuringDirective httpsDirective;
    private final RequestTimeoutHandlingDirective requestTimeoutHandlingDirective;
    private final ExceptionHandler exceptionHandler;
    private final Map<Integer, JsonSchemaVersion> supportedSchemaVersions;
    private final ProtocolAdapterProvider protocolAdapterProvider;
    private final Function<DittoRuntimeException, HttpResponse> dreToHttpResponse;
    private final CustomHeadersHandler customHeadersHandler;
    private final RejectionHandler rejectionHandler;
    private final RootRouteHeadersStepBuilder rootRouteHeadersStepBuilder;

    private RootRoute(final Builder builder) {
        final HttpConfig httpConfig = builder.httpConfig;
        ownStatusRoute = builder.statusRoute;
        overallStatusRoute = builder.overallStatusRoute;
        cachingHealthRoute = builder.cachingHealthRoute;
        devopsRoute = builder.devopsRoute;
        policiesRoute = builder.policiesRoute;
        sseThingsRouteBuilder = builder.sseThingsRouteBuilder;
        thingsRoute = builder.thingsRoute;
        thingSearchRoute = builder.thingSearchRoute;
        connectionsRoute = builder.connectionsRoute;
        websocketRouteBuilder = builder.websocketRouteBuilder;
        statsRoute = builder.statsRoute;
        whoamiRoute = builder.whoamiRoute;
        cloudEventsRoute = builder.cloudEventsRoute;
        customApiRoutesProvider = builder.customApiRoutesProvider;
        routeBaseProperties = builder.routeBaseProperties;
        apiAuthenticationDirective = builder.httpAuthenticationDirective;
        wsAuthenticationDirective = builder.wsAuthenticationDirective;
        requestTimeoutHandlingDirective = RequestTimeoutHandlingDirective.getInstance(httpConfig);
        httpsDirective = HttpsEnsuringDirective.getInstance(httpConfig);
        corsDirective = CorsEnablingDirective.getInstance(httpConfig);
        supportedSchemaVersions = new HashMap<>(builder.supportedSchemaVersions);
        protocolAdapterProvider = builder.protocolAdapterProvider;
        dreToHttpResponse = DittoRuntimeExceptionToHttpResponse.getInstance(builder.headerTranslator);
        exceptionHandler = null != builder.exceptionHandler
                ? builder.exceptionHandler
                : RootRouteExceptionHandler.getInstance(dreToHttpResponse);
        customHeadersHandler = builder.customHeadersHandler;
        rejectionHandler = builder.rejectionHandler;
        rootRouteHeadersStepBuilder = RootRouteHeadersStepBuilder.getInstance(builder.headerTranslator,
                QueryParametersToHeadersMap.getInstance(httpConfig),
                customHeadersHandler,
                builder.dittoHeadersValidator);
    }

    public static RootRouteBuilder getBuilder(final HttpConfig httpConfig) {
        return new Builder(httpConfig)
                .customHeadersHandler(NoopCustomHeadersHandler.getInstance())
                .rejectionHandler(DittoRejectionHandlerFactory.createInstance());
    }

    private static Route newRouteInstance(final Builder builder) {
        final RootRoute rootRoute = new RootRoute(builder);
        return rootRoute.buildRoute();
    }

    private Route buildRoute() {
        return wrapWithRootDirectives(correlationId ->
                parameterMap(queryParameters ->
                        extractRequestContext(ctx ->
                                concat(
                                        statsRoute.buildStatsRoute(correlationId), // /stats
                                        cachingHealthRoute.buildHealthRoute(), // /health
                                        connections(ctx, correlationId, queryParameters), // /api/2/connections
                                        api(ctx, correlationId, queryParameters), // /api
                                        ws(ctx, correlationId, queryParameters), // /ws
                                        ownStatusRoute.buildStatusRoute(), // /status
                                        overallStatusRoute.buildOverallStatusRoute(), // /overall
                                        devopsRoute.buildDevOpsRoute(ctx, queryParameters) // /devops
                                )
                        )
                )
        );
    }

    private Route wrapWithRootDirectives(final Function<String, Route> rootRoute) {

        final Function<Function<String, Route>, Route> outerRouteProvider = innerRouteProvider ->
                /* the outer handleExceptions is for handling exceptions in the directives wrapping the rootRoute
                   (which normally should not occur */
                handleExceptions(exceptionHandler, () ->
                        CorrelationIdEnsuringDirective.ensureCorrelationId(
                                correlationId -> requestTimeoutHandlingDirective
                                        .handleRequestTimeout(correlationId, () ->
                                                RequestTracingDirective.traceRequest(
                                                        () -> RequestResultLoggingDirective.logRequestResult(
                                                                correlationId,
                                                                () -> innerRouteProvider.apply(correlationId)
                                                        ),
                                                        correlationId
                                                )
                                        )
                        )
                );

        final Function<String, Route> innerRouteProvider = correlationId ->
                EncodingEnsuringDirective.ensureEncoding(() ->
                        httpsDirective.ensureHttps(correlationId, () ->
                                corsDirective.enableCors(() ->
                                        /* handling the rejections is done by akka automatically, but if we
                                           do it here explicitly, we are able to log the status code for the
                                           rejection (e.g. 404 or 405) in a wrapping directive. */
                                        handleRejections(rejectionHandler, () ->
                                                /* the inner handleExceptions is for handling exceptions
                                                   occurring in the route route. It makes sure that the
                                                   wrapping directives such as addSecurityResponseHeaders are
                                                   even called in an error case in the route route. */
                                                handleExceptions(exceptionHandler, () ->
                                                        rootRoute.apply(correlationId)
                                                )
                                        )
                                )
                        )
                );
        return outerRouteProvider.apply(innerRouteProvider);
    }

    /*
     * Describes {@code /api/2/connections} route.
     *
     * @return route for API resource.
     */
    private Route connections(final RequestContext ctx, final String correlationId,
            final Map<String, String> queryParameters) {

        return rawPathPrefix(PathMatchers.slash().concat(HTTP_PATH_API_PREFIX), () -> // /api
                ensureSchemaVersion(apiVersion ->  // /api/<apiVersion>
                        rawPathPrefixTest(PathMatchers.slash().concat(ConnectionsRoute.PATH_CONNECTIONS),
                                () -> // /api/<apiVersion>/connections
                                        withDittoHeaders(rootRouteHeadersStepBuilder.withInitialDittoHeadersBuilder(
                                                                DittoHeaders.newBuilder()
                                                                        .schemaVersion(apiVersion)
                                                                        .correlationId(correlationId)
                                                                        .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(),
                                                                                Boolean.TRUE.toString())
                                                        )
                                                        .withRequestContext(ctx)
                                                        .withQueryParameters(queryParameters)
                                                        .build(CustomHeadersHandler.RequestType.API),
                                                dittoHeaders -> connectionsRoute.buildConnectionsRoute(ctx,
                                                        dittoHeaders)
                                        ).seal()
                                // sealing here is important as we don't want to fall back to other routes if devops auth failed
                        )
                )
        );
    }

    /*
     * Describes {@code /api} route.
     *
     * @return route for API resource.
     */
    private Route api(final RequestContext ctx, final CharSequence correlationId,
            final Map<String, String> queryParameters) {

        return rawPathPrefix(PathMatchers.slash().concat(HTTP_PATH_API_PREFIX), () -> // /api
                ensureSchemaVersion(apiVersion ->  // /api/<apiVersion>
                        customApiRoutesProvider
                                .unauthorized(routeBaseProperties, apiVersion, correlationId)
                                .orElse(apiAuthentication(apiVersion, correlationId, auth ->
                                        withDittoHeaders(
                                                rootRouteHeadersStepBuilder.withInitialDittoHeadersBuilder(
                                                                auth.getDittoHeaders().toBuilder()
                                                        )
                                                        .withRequestContext(ctx)
                                                        .withQueryParameters(queryParameters)
                                                        .build(CustomHeadersHandler.RequestType.API),
                                                dittoHeaders -> buildApiSubRoutes(ctx, dittoHeaders, auth)
                                        )
                                ))
                )
        );
    }

    private Route ensureSchemaVersion(final Function<JsonSchemaVersion, Route> inner) {
        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.integerSegment()),
                apiVersion -> { // /xx/<schemaVersion>
                    @Nullable final JsonSchemaVersion jsonSchemaVersion = supportedSchemaVersions.get(apiVersion);
                    if (null == jsonSchemaVersion) {
                        final DittoRuntimeException dre = CommandNotSupportedException.newBuilder(apiVersion).build();
                        return complete(dreToHttpResponse.apply(dre));
                    }
                    try {
                        return inner.apply(jsonSchemaVersion);
                    } catch (final RuntimeException e) {
                        throw e; // rethrow RuntimeExceptions
                    } catch (final Exception e) {
                        throw new IllegalStateException("Unexpected checked exception", e);
                    }
                });
    }

    private Route apiAuthentication(final JsonSchemaVersion schemaVersion, final CharSequence correlationId,
            final Function<AuthenticationResult, Route> inner) {

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(schemaVersion)
                .correlationId(correlationId)
                .build();
        return apiAuthenticationDirective.authenticate(dittoHeaders, inner);
    }

    private Route buildApiSubRoutes(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final AuthenticationResult authenticationResult) {

        final Route customApiSubRoutes = customApiRoutesProvider.authorized(routeBaseProperties, dittoHeaders);

        return concat(
                // /api/{apiVersion}/policies
                policiesRoute.buildPoliciesRoute(ctx, dittoHeaders, authenticationResult),
                // /api/{apiVersion}/things SSE support
                buildSseThingsRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/things
                thingsRoute.buildThingsRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/search/things
                thingSearchRoute.buildSearchRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/whoami
                whoamiRoute.buildWhoamiRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/cloudevents
                cloudEventsRoute.buildCloudEventsRoute(ctx, dittoHeaders)
        ).orElse(customApiSubRoutes);
    }

    private Route buildSseThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {

        return handleExceptions(exceptionHandler,
                () -> sseThingsRouteBuilder.build(ctx,
                        () -> overwriteDittoHeadersForSse(ctx, dittoHeaders)));
    }

    private CompletionStage<DittoHeaders> overwriteDittoHeadersForSse(final RequestContext ctx,
            final DittoHeaders dittoHeaders) {

        final String correlationId = dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());

        return customHeadersHandler.handleCustomHeaders(correlationId, ctx, CustomHeadersHandler.RequestType.SSE,
                dittoHeaders);
    }

    /*
     * Describes {@code /ws} route.
     *
     * @return route for Websocket resource.
     */
    private Route ws(final RequestContext ctx, final CharSequence correlationId,
            final Map<String, String> queryParameters) {
        return rawPathPrefix(PathMatchers.slash().concat(WS_PATH_PREFIX), () -> // /ws
                ensureSchemaVersion(wsVersion -> // /ws/<wsVersion>
                        wsAuthentication(wsVersion, correlationId, auth -> {
                                    final CompletionStage<DittoHeaders> dittoHeadersPromise =
                                            rootRouteHeadersStepBuilder
                                                    .withInitialDittoHeadersBuilder(auth.getDittoHeaders().toBuilder())
                                                    .withRequestContext(ctx)
                                                    .withQueryParameters(queryParameters)
                                                    .build(CustomHeadersHandler.RequestType.WS);

                                    return withDittoHeaders(dittoHeadersPromise, dittoHeaders -> {
                                        @Nullable final String userAgent = getUserAgentOrNull(ctx);
                                        final ProtocolAdapter chosenProtocolAdapter =
                                                protocolAdapterProvider.getProtocolAdapter(userAgent);
                                        return websocketRouteBuilder.build(wsVersion, correlationId, dittoHeaders,
                                                chosenProtocolAdapter, ctx);
                                    });
                                }
                        )
                )
        );
    }

    private Route wsAuthentication(final JsonSchemaVersion schemaVersion, final CharSequence correlationId,
            final Function<AuthenticationResult, Route> inner) {

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(schemaVersion)
                .correlationId(correlationId)
                .build();
        return wsAuthenticationDirective.authenticate(dittoHeaders, inner);
    }

    @Nullable
    private static String getUserAgentOrNull(final RequestContext requestContext) {
        final HttpRequest request = requestContext.getRequest();
        for (final HttpHeader header : request.getHeaders()) {

            // find user-agent: HTTP header names are case-insensitive
            if ("user-agent".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }

    private Route withDittoHeaders(final CompletionStage<DittoHeaders> dittoHeadersPromise,
            final Function<DittoHeaders, Route> inner) {

        return handleExceptions(exceptionHandler,
                () -> javaFunctionToRoute(
                        requestContext -> dittoHeadersPromise
                                .thenApply(inner)
                                .thenCompose(route -> routeToJavaFunction(route).apply(requestContext))));
    }

    private static Route javaFunctionToRoute(final Function<akka.http.scaladsl.server.RequestContext,
            CompletionStage<RouteResult>> function) {

        final Function1<akka.http.scaladsl.server.RequestContext, Future<RouteResult>> scalaFunction =
                new PFBuilder<akka.http.scaladsl.server.RequestContext, Future<RouteResult>>()
                        .matchAny(scalaRequestContext -> FutureConverters.toScala(function.apply(scalaRequestContext)))
                        .build();
        return RouteAdapter.asJava(scalaFunction);
    }

    private static Function<akka.http.scaladsl.server.RequestContext, CompletionStage<RouteResult>> routeToJavaFunction(
            final Route route) {

        return scalaRequestContext ->
                scala.compat.java8.FutureConverters.toJava(route.asScala().apply(scalaRequestContext));
    }

    @NotThreadSafe
    private static final class Builder implements RootRouteBuilder {

        private final HttpConfig httpConfig;
        private StatusRoute statusRoute;
        private OverallStatusRoute overallStatusRoute;
        private CachingHealthRoute cachingHealthRoute;
        private DevOpsRoute devopsRoute;

        private PoliciesRoute policiesRoute;
        private SseRouteBuilder sseThingsRouteBuilder;
        private ThingsRoute thingsRoute;
        private ThingSearchRoute thingSearchRoute;
        private ConnectionsRoute connectionsRoute;
        private WebSocketRouteBuilder websocketRouteBuilder;
        private StatsRoute statsRoute;
        private WhoamiRoute whoamiRoute;
        private CloudEventsRoute cloudEventsRoute;

        private CustomApiRoutesProvider customApiRoutesProvider;
        private RouteBaseProperties routeBaseProperties;
        private GatewayAuthenticationDirective httpAuthenticationDirective;
        private GatewayAuthenticationDirective wsAuthenticationDirective;
        private ExceptionHandler exceptionHandler;
        private final Map<Integer, JsonSchemaVersion> supportedSchemaVersions = new HashMap<>();
        private ProtocolAdapterProvider protocolAdapterProvider;
        private HeaderTranslator headerTranslator;
        private CustomHeadersHandler customHeadersHandler;
        private RejectionHandler rejectionHandler;

        private DittoHeadersValidator dittoHeadersValidator;

        private Builder(final HttpConfig httpConfig) {
            this.httpConfig = httpConfig;
        }

        @Override
        public RootRouteBuilder statusRoute(final StatusRoute route) {
            statusRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder overallStatusRoute(final OverallStatusRoute route) {
            overallStatusRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder cachingHealthRoute(final CachingHealthRoute route) {
            cachingHealthRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder devopsRoute(final DevOpsRoute route) {
            devopsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder policiesRoute(final PoliciesRoute route) {
            policiesRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder sseThingsRoute(final SseRouteBuilder sseThingsRouteBuilder) {
            this.sseThingsRouteBuilder = sseThingsRouteBuilder;
            return this;
        }

        @Override
        public RootRouteBuilder thingsRoute(final ThingsRoute route) {
            thingsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder thingSearchRoute(final ThingSearchRoute route) {
            thingSearchRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder connectionsRoute(final ConnectionsRoute route) {
            connectionsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder websocketRoute(final WebSocketRouteBuilder websocketRouteBuilder) {
            this.websocketRouteBuilder = websocketRouteBuilder;
            return this;
        }

        @Override
        public RootRouteBuilder statsRoute(final StatsRoute route) {
            statsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder whoamiRoute(final WhoamiRoute route) {
            whoamiRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder cloudEventsRoute(final CloudEventsRoute route) {
            cloudEventsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder customApiRoutesProvider(final CustomApiRoutesProvider provider,
                final RouteBaseProperties routeBaseProperties) {

            customApiRoutesProvider = provider;
            this.routeBaseProperties = routeBaseProperties;
            return this;
        }

        @Override
        public RootRouteBuilder httpAuthenticationDirective(final GatewayAuthenticationDirective directive) {
            httpAuthenticationDirective = directive;
            return this;
        }

        @Override
        public RootRouteBuilder wsAuthenticationDirective(final GatewayAuthenticationDirective directive) {
            wsAuthenticationDirective = directive;
            return this;
        }

        @Override
        public RootRouteBuilder exceptionHandler(final ExceptionHandler handler) {
            exceptionHandler = handler;
            return this;
        }

        @Override
        public RootRouteBuilder supportedSchemaVersions(final Collection<JsonSchemaVersion> versions) {
            checkNotNull(versions, "versions");
            versions.forEach(schemaVersion -> supportedSchemaVersions.put(schemaVersion.toInt(), schemaVersion));
            return this;
        }

        @Override
        public RootRouteBuilder protocolAdapterProvider(final ProtocolAdapterProvider provider) {
            protocolAdapterProvider = provider;
            return this;
        }

        @Override
        public RootRouteBuilder headerTranslator(final HeaderTranslator translator) {
            headerTranslator = translator;
            return this;
        }

        @Override
        public RootRouteBuilder customHeadersHandler(final CustomHeadersHandler handler) {
            customHeadersHandler = handler;
            return this;
        }

        @Override
        public RootRouteBuilder rejectionHandler(final RejectionHandler handler) {
            rejectionHandler = handler;
            return this;
        }

        @Override
        public RootRouteBuilder dittoHeadersValidator(final DittoHeadersValidator dittoHeadersValidator) {
            this.dittoHeadersValidator = dittoHeadersValidator;
            return this;
        }

        @Override
        public Route build() {
            return newRouteInstance(this);
        }

    }

}
