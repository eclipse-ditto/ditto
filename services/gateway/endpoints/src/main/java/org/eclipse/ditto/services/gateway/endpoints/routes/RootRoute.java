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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.handleExceptions;
import static akka.http.javadsl.server.Directives.handleRejections;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CorrelationIdEnsuringDirective.ensureCorrelationId;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.RequestResultLoggingDirective.logRequestResult;
import static org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthorizationContextVersioningDirective.mapAuthorizationContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.CorsEnablingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.EncodingEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.RequestTimeoutHandlingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.SecurityResponseHeadersDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.DittoRejectionHandlerFactory;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayDuplicateHeaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMessage;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.util.ByteString;

/**
 * Builder for creating Akka HTTP routes for {@code /}.
 */
public final class RootRoute {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootRoute.class);

    static final String HTTP_PATH_API_PREFIX = "api";
    static final String WS_PATH_PREFIX = "ws";

    private final HttpConfig httpConfig;

    private final StatusRoute ownStatusRoute;
    private final OverallStatusRoute overallStatusRoute;
    private final CachingHealthRoute cachingHealthRoute;
    private final DevOpsRoute devopsRoute;

    private final PoliciesRoute policiesRoute;
    private final SseThingsRoute sseThingsRoute;
    private final ThingsRoute thingsRoute;
    private final ThingSearchRoute thingSearchRoute;
    private final WebsocketRoute websocketRoute;
    private final StatsRoute statsRoute;

    private final CustomApiRoutesProvider customApiRoutesProvider;
    private final GatewayAuthenticationDirective apiAuthenticationDirective;
    private final GatewayAuthenticationDirective wsAuthenticationDirective;
    private final ExceptionHandler exceptionHandler;
    private final Set<Integer> supportedSchemaVersions;
    private final ProtocolAdapterProvider protocolAdapterProvider;
    private final HeaderTranslator headerTranslator;
    private final CustomHeadersHandler customHeadersHandler;
    private final RejectionHandler rejectionHandler;

    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private RootRoute(final Builder builder) {
        httpConfig = builder.httpConfig;
        ownStatusRoute = builder.statusRoute;
        overallStatusRoute = builder.overallStatusRoute;
        cachingHealthRoute = builder.cachingHealthRoute;
        devopsRoute = builder.devopsRoute;
        policiesRoute = builder.policiesRoute;
        sseThingsRoute = builder.sseThingsRoute;
        thingsRoute = builder.thingsRoute;
        thingSearchRoute = builder.thingSearchRoute;
        websocketRoute = builder.websocketRoute;
        statsRoute = builder.statsRoute;
        customApiRoutesProvider = builder.customApiRoutesProvider;
        apiAuthenticationDirective = builder.httpAuthenticationDirective;
        wsAuthenticationDirective = builder.wsAuthenticationDirective;
        exceptionHandler = null != builder.exceptionHandler ? builder.exceptionHandler : createExceptionHandler();
        supportedSchemaVersions = new HashSet<>(builder.supportedSchemaVersions);
        protocolAdapterProvider = builder.protocolAdapterProvider;
        headerTranslator = builder.headerTranslator;
        customHeadersHandler = builder.customHeadersHandler;
        rejectionHandler = builder.rejectionHandler;
        dittoHeadersSizeChecker = checkNotNull(builder.dittoHeadersSizeChecker, "dittoHeadersSizeChecker");
    }

    public static RootRouteBuilder getBuilder(final HttpConfig httpConfig) {
        return new Builder(httpConfig)
                .customApiRoutesProvider(NoopCustomApiRoutesProvider.getInstance())
                .customHeadersHandler(NoopCustomHeadersHandler.getInstance())
                .rejectionHandler(DittoRejectionHandlerFactory.createInstance());
    }

    private static Route newRouteInstance(final Builder builder) {
        final RootRoute rootRoute = new RootRoute(builder);
        return rootRoute.buildRoute();
    }

    private Route buildRoute() {
        return wrapWithRootDirectives(correlationId ->
                extractRequestContext(ctx ->
                        route(
                                statsRoute.buildStatsRoute(correlationId), // /stats
                                cachingHealthRoute.buildHealthRoute(), // /health
                                api(ctx, correlationId), // /api
                                ws(ctx, correlationId), // /ws
                                ownStatusRoute.buildStatusRoute(), // /status
                                overallStatusRoute.buildOverallStatusRoute(), // /overall
                                devopsRoute.buildDevOpsRoute(ctx) // /devops
                        )
                )
        );
    }

    private Route wrapWithRootDirectives(final java.util.function.Function<String, Route> rootRoute) {
        final Function<Function<String, Route>, Route> outerRouteProvider = innerRouteProvider ->
                /* the outer handleExceptions is for handling exceptions in the directives wrapping the rootRoute
                   (which normally should not occur */
                handleExceptions(exceptionHandler, () ->
                        ensureCorrelationId(correlationId -> {
                            final RequestTimeoutHandlingDirective requestTimeoutHandlingDirective =
                                    RequestTimeoutHandlingDirective.getInstance(httpConfig);
                            return requestTimeoutHandlingDirective.handleRequestTimeout(correlationId, () ->
                                    logRequestResult(correlationId, () -> innerRouteProvider.apply(correlationId))
                            );
                        })
                );

        final Function<String, Route> innerRouteProvider = correlationId ->
                EncodingEnsuringDirective.ensureEncoding(correlationId, () -> {
                    final HttpsEnsuringDirective httpsDirective = HttpsEnsuringDirective.getInstance(httpConfig);
                    final CorsEnablingDirective corsDirective = CorsEnablingDirective.getInstance(httpConfig);
                    return httpsDirective.ensureHttps(correlationId, () ->
                            corsDirective.enableCors(() ->
                                    SecurityResponseHeadersDirective.addSecurityResponseHeaders(() ->
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
                });
        return outerRouteProvider.apply(innerRouteProvider);
    }

    private Route apiAuthentication(final CharSequence correlationId,
            final Function<AuthorizationContext, Route> inner) {

        return apiAuthenticationDirective.authenticate(correlationId, inner);
    }

    private Route wsAuthentication(final CharSequence correlationId,
            final Function<AuthorizationContext, Route> inner) {

        return wsAuthenticationDirective.authenticate(correlationId, inner);
    }

    /*
     * Describes {@code /api} route.
     *
     * @return route for API resource.
     */
    private Route api(final RequestContext ctx, final String correlationId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(HTTP_PATH_API_PREFIX), () -> // /api
                ensureSchemaVersion(apiVersion -> // /api/<apiVersion>
                        customApiRoutesProvider.unauthorized(apiVersion, correlationId).orElse(
                                apiAuthentication(correlationId,
                                        authContextWithPrefixedSubjects ->
                                                mapAuthorizationContext(
                                                        correlationId,
                                                        apiVersion,
                                                        authContextWithPrefixedSubjects,
                                                        authContext ->
                                                                parameterOptional(TopicPath.Channel.LIVE.getName(),
                                                                        liveParam ->
                                                                                withDittoHeaders(
                                                                                        authContext,
                                                                                        apiVersion,
                                                                                        correlationId,
                                                                                        ctx,
                                                                                        liveParam.orElse(null),
                                                                                        CustomHeadersHandler.RequestType.API,
                                                                                        dittoHeaders ->
                                                                                                buildApiSubRoutes(ctx,
                                                                                                        dittoHeaders,
                                                                                                        authContext)
                                                                                )
                                                                )
                                                )
                                ))
                )
        );
    }

    private Route ensureSchemaVersion(final IntFunction<Route> inner) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.integerSegment()),
                apiVersion -> { // /xx/<schemaVersion>
                    if (supportedSchemaVersions.contains(apiVersion)) {
                        try {
                            return inner.apply(apiVersion);
                        } catch (final RuntimeException e) {
                            throw e; // rethrow RuntimeExceptions
                        } catch (final Exception e) {
                            throw new IllegalStateException("Unexpected checked exception", e);
                        }
                    }
                    return complete(getHttpResponseFor(CommandNotSupportedException.newBuilder(apiVersion).build()));
                });
    }

    private HttpResponse getHttpResponseFor(final DittoRuntimeException exception) {
        return HttpResponse.create()
                .withStatus(exception.getStatusCode().toInt())
                .withHeaders(getExternalHeadersFor(exception.getDittoHeaders()))
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(exception.toJsonString()));
    }

    private Set<HttpHeader> getExternalHeadersFor(final DittoHeaders dittoHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(dittoHeaders);
        return externalHeaders.entrySet()
                .stream()
                .map(headerEntry -> HttpHeader.parse(headerEntry.getKey(), headerEntry.getValue()))
                .collect(Collectors.toSet());
    }

    private Route buildApiSubRoutes(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {

        final Route customApiSubRoutes = customApiRoutesProvider.authorized(dittoHeaders);

        return Directives.route(
                // /api/{apiVersion}/policies
                policiesRoute.buildPoliciesRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/things SSE support
                sseThingsRoute.buildThingsSseRoute(ctx, () ->
                        overwriteDittoHeaders(ctx, dittoHeaders, authorizationContext)),
                // /api/{apiVersion}/things
                thingsRoute.buildThingsRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/search/things
                thingSearchRoute.buildSearchRoute(ctx, dittoHeaders)
        ).orElse(customApiSubRoutes);
    }

    /*
     * Describes {@code /ws} route.
     *
     * @return route for Websocket resource.
     */
    private Route ws(final RequestContext ctx, final String correlationId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(WS_PATH_PREFIX), () -> // /ws
                ensureSchemaVersion(wsVersion -> // /ws/<wsVersion>
                        wsAuthentication(correlationId, authContextWithPrefixedSubjects ->
                                mapAuthorizationContext(correlationId, wsVersion, authContextWithPrefixedSubjects,
                                        authContext ->
                                                withDittoHeaders(authContext,
                                                        wsVersion, correlationId, ctx, null,
                                                        CustomHeadersHandler.RequestType.WS, dittoHeaders -> {

                                                            final String userAgent = extractUserAgent(ctx).orElse(null);
                                                            final ProtocolAdapter chosenProtocolAdapter =
                                                                    protocolAdapterProvider.getProtocolAdapter(
                                                                            userAgent);
                                                            return websocketRoute.buildWebsocketRoute(wsVersion,
                                                                    correlationId,
                                                                    authContext, dittoHeaders, chosenProtocolAdapter);
                                                        }
                                                )
                                )
                        )
                )
        );
    }

    private static Optional<String> extractUserAgent(final RequestContext requestContext) {
        final Stream<HttpHeader> headerStream =
                StreamSupport.stream(requestContext.getRequest().getHeaders().spliterator(), false);
        // find user-agent: HTTP header names are case-insensitive
        return headerStream.filter(header -> "user-agent".equalsIgnoreCase(header.name()))
                .map(HttpHeader::value)
                .findAny();
    }

    private Route withDittoHeaders(final AuthorizationContext authorizationContext,
            final Integer version,
            final String correlationId,
            final RequestContext ctx,
            @Nullable final String liveParam,
            final CustomHeadersHandler.RequestType requestType,
            final Function<DittoHeaders, Route> inner) {

        final DittoHeaders dittoHeaders =
                buildDittoHeaders(authorizationContext, version, correlationId, ctx, liveParam, requestType);

        return handleExceptions(exceptionHandler, () -> {
           dittoHeadersSizeChecker.check(dittoHeaders);
            return inner.apply(dittoHeaders);
        });
    }

    private DittoHeaders overwriteDittoHeaders(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {

        final String correlationId = dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());

        return handleCustomHeaders(correlationId, ctx, CustomHeadersHandler.RequestType.SSE, authorizationContext,
                dittoHeaders);
    }

    private DittoHeaders buildDittoHeaders(final AuthorizationContext authorizationContext,
            final Integer version,
            final String correlationId,
            final RequestContext ctx,
            @Nullable final String liveParam,
            final CustomHeadersHandler.RequestType requestType) {

        final DittoHeadersBuilder builder = DittoHeaders.newBuilder();

        final Map<String, String> externalHeadersMap = getFilteredExternalHeaders(ctx.getRequest(), correlationId);
        builder.putHeaders(externalHeadersMap);

        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());

        builder.authorizationContext(authorizationContext)
                .schemaVersion(jsonSchemaVersion)
                .correlationId(correlationId);

        authorizationContext.getFirstAuthorizationSubject().map(AuthorizationSubject::getId).ifPresent(builder::source);

        // if the "live" query param was set - no matter what the value was - use live channel
        if (liveParam != null) {
            builder.channel(TopicPath.Channel.LIVE.getName());
        }

        final DittoHeaders dittoDefaultHeaders = builder.build();
        return handleCustomHeaders(correlationId, ctx, requestType, authorizationContext, dittoDefaultHeaders);
    }

    private DittoHeaders handleCustomHeaders(final String correlationId, final RequestContext ctx,
            final CustomHeadersHandler.RequestType requestType,
            final AuthorizationContext authorizationContext,
            final DittoHeaders dittoDefaultHeaders) {

        return customHeadersHandler.handleCustomHeaders(correlationId, ctx, requestType,
                authorizationContext, dittoDefaultHeaders);
    }

    private Map<String, String> getFilteredExternalHeaders(final HttpMessage httpRequest, final String correlationId) {
        final Map<String, String> externalHeaders =
                StreamSupport.stream(httpRequest.getHeaders().spliterator(), false)
                        .collect(Collectors.toMap(HttpHeader::lowercaseName, HttpHeader::value, (dv1, dv2) -> {
                            throw GatewayDuplicateHeaderException
                                    .newBuilder()
                                    .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                                    .build();
                        }));
        return headerTranslator.fromExternalHeaders(externalHeaders);
    }

    private ExceptionHandler createExceptionHandler() {
        return ExceptionHandler.newBuilder()
                .match(DittoRuntimeException.class, dittoRuntimeException -> {
                    logException(dittoRuntimeException);
                    return complete(getHttpResponseFor(dittoRuntimeException));
                })
                .match(JsonRuntimeException.class, jsonRuntimeException -> {
                    final DittoRuntimeException dittoRuntimeException = new DittoJsonException(jsonRuntimeException);
                    logException(dittoRuntimeException);
                    return complete(getHttpResponseFor(dittoRuntimeException));
                })
                .matchAny(throwable -> {
                    LOGGER.error("Unexpected RuntimeException in gateway root route: <{}>!", throwable.getMessage(),
                            throwable);

                    return complete(StatusCodes.INTERNAL_SERVER_ERROR);
                })
                .build();
    }

    private static void logException(final DittoRuntimeException exception) {
        final DittoHeaders dittoHeaders = exception.getDittoHeaders();

        final Optional<String> correlationIdOptional = dittoHeaders.getCorrelationId();
        final String simpleExceptionName = exception.getClass().getSimpleName();
        final String exceptionMessage = exception.getMessage();
        if (!correlationIdOptional.isPresent()) {
            LOGGER.warn("Correlation ID was missing in headers of <{}>: <{}>!", simpleExceptionName, exceptionMessage);
        }
        enhanceLogWithCorrelationId(correlationIdOptional,
                () -> LOGGER.info("<{}> occurred in gateway root route: <{}>!", simpleExceptionName, exceptionMessage));
    }

    @NotThreadSafe
    private static final class Builder implements RootRouteBuilder {

        private final HttpConfig httpConfig;
        private StatusRoute statusRoute;
        private OverallStatusRoute overallStatusRoute;
        private CachingHealthRoute cachingHealthRoute;
        private DevOpsRoute devopsRoute;

        private PoliciesRoute policiesRoute;
        private SseThingsRoute sseThingsRoute;
        private ThingsRoute thingsRoute;
        private ThingSearchRoute thingSearchRoute;
        private WebsocketRoute websocketRoute;
        private StatsRoute statsRoute;

        private CustomApiRoutesProvider customApiRoutesProvider;
        private GatewayAuthenticationDirective httpAuthenticationDirective;
        private GatewayAuthenticationDirective wsAuthenticationDirective;
        private ExceptionHandler exceptionHandler;
        private Collection<Integer> supportedSchemaVersions;
        private ProtocolAdapterProvider protocolAdapterProvider;
        private HeaderTranslator headerTranslator;
        private CustomHeadersHandler customHeadersHandler;
        private RejectionHandler rejectionHandler;

        private DittoHeadersSizeChecker dittoHeadersSizeChecker;

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
        public RootRouteBuilder sseThingsRoute(final SseThingsRoute route) {
            sseThingsRoute = route;
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
        public RootRouteBuilder websocketRoute(final WebsocketRoute route) {
            websocketRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder statsRoute(final StatsRoute route) {
            statsRoute = route;
            return this;
        }

        @Override
        public RootRouteBuilder customApiRoutesProvider(final CustomApiRoutesProvider provider) {
            customApiRoutesProvider = provider;
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
        public RootRouteBuilder supportedSchemaVersions(final Collection<Integer> versions) {
            supportedSchemaVersions = versions;
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
        public RootRouteBuilder dittoHeadersSizeChecker(final DittoHeadersSizeChecker checker) {
            dittoHeadersSizeChecker = checker;
            return this;
        }

        @Override
        public Route build() {
            return newRouteInstance(this);
        }

    }

}
