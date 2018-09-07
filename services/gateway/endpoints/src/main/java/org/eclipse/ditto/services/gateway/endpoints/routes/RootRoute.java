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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.handleExceptions;
import static akka.http.javadsl.server.Directives.handleRejections;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.Directives.pathPrefixTest;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CorrelationIdEnsuringDirective.ensureCorrelationId;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.REALM_DEVOPS;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.authenticateDevopsBasic;
import static org.eclipse.ditto.services.gateway.endpoints.directives.RequestResultLoggingDirective.logRequestResult;
import static org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthorizationContextVersioningDirective.mapAuthorizationContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.directives.CorsEnablingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.EncodingEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.RequestTimeoutHandlingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.SecurityResponseHeadersDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.DittoGatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
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
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.ProtocolConfigReader;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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

    static final String HTTP_PATH_API_PREFIX = "api";
    static final String WS_PATH_PREFIX = "ws";

    private static final Logger LOGGER = LoggerFactory.getLogger(RootRoute.class);

    /**
     * Contains a Pattern which routes are included in devops Basic Auth secured resources.
     */
    public static final Pattern DEVOPS_AUTH_SECURED = Pattern.compile("(" +
            OverallStatusRoute.PATH_STATUS + "|" +
            DevOpsRoute.PATH_DEVOPS + ").*"
    );

    private final StatusRoute ownStatusRoute;
    private final OverallStatusRoute overallStatusRoute;
    private final CachingHealthRoute cachingHealthRoute;
    private final DevOpsRoute devopsRoute;

    private final PoliciesRoute policiesRoute;
    private final SseThingsRoute sseThingsRoute;
    private final ThingsRoute thingsRoute;
    private final ThingSearchRoute thingSearchRoute;
    private final WebsocketRoute websocketRoute;
    private final CustomApiRoutesProvider customApiRoutesProvider;
    private final GatewayAuthenticationDirective apiAuthenticationDirective;
    private final GatewayAuthenticationDirective wsAuthenticationDirective;
    private final StatsRoute statsRoute;
    private final ExceptionHandler exceptionHandler;
    private final List<Integer> supportedSchemaVersions;
    private final RejectionHandler rejectionHandler = DittoRejectionHandlerFactory.createInstance();
    private final ProtocolAdapterProvider protocolAdapterProvider;
    private final HeaderTranslator headerTranslator;
    private final CustomHeadersHandler customHeadersHandler;

    /**
     * Constructs the {@code /} route builder.
     *
     * @param actorSystem the Actor System.
     * @param config the configuration of the service.
     * @param proxyActor the proxy actor delegating commands.
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param healthCheckingActor the health-checking actor to use.
     * @param clusterStateSupplier the supplier to get the cluster state.
     * @param httpClient the Http Client to use.
     */
    public RootRoute(final ActorSystem actorSystem, final Config config,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final Supplier<ClusterStatus> clusterStateSupplier,
            final HttpClientFacade httpClient) {
        this(actorSystem, config, proxyActor, streamingActor, healthCheckingActor, clusterStateSupplier,
                new DittoGatewayAuthenticationDirectiveFactory(config, httpClient),
                NoopCustomApiRoutesProvider.getInstance(),NoopCustomHeadersHandler.getInstance());
    }

    /**
     * Constructs the {@code /} route builder.
     *
     * @param actorSystem the Actor System.
     * @param config the configuration of the service.
     * @param proxyActor the proxy actor delegating commands.
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param healthCheckingActor the health-checking actor to use.
     * @param clusterStateSupplier the supplier to get the cluster state.
     */
    public RootRoute(final ActorSystem actorSystem, final Config config,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final Supplier<ClusterStatus> clusterStateSupplier,
            final GatewayAuthenticationDirectiveFactory gatewayAuthenticationDirectiveFactory,
            final CustomApiRoutesProvider customApiRoutesProvider,
            final CustomHeadersHandler customHeadersHandler) {
        checkNotNull(actorSystem, "Actor System");
        checkNotNull(proxyActor, "proxyActor");

        final StatusAndHealthProvider
                statusHealthProvider = DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier);

        statsRoute = new StatsRoute(proxyActor, actorSystem);
        ownStatusRoute = new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem);
        overallStatusRoute = new OverallStatusRoute(actorSystem, clusterStateSupplier, healthCheckingActor,
                statusHealthProvider);
        cachingHealthRoute = new CachingHealthRoute(statusHealthProvider,
                config.getDuration(ConfigKeys.STATUS_HEALTH_EXTERNAL_CACHE_TIMEOUT));
        devopsRoute = new DevOpsRoute(proxyActor, actorSystem);

        policiesRoute = new PoliciesRoute(proxyActor, actorSystem);
        sseThingsRoute = new SseThingsRoute(proxyActor, actorSystem, streamingActor);
        thingsRoute = new ThingsRoute(proxyActor, actorSystem,
                config.getDuration(ConfigKeys.MESSAGE_DEFAULT_TIMEOUT),
                config.getDuration(ConfigKeys.MESSAGE_MAX_TIMEOUT),
                config.getDuration(ConfigKeys.CLAIMMESSAGE_DEFAULT_TIMEOUT),
                config.getDuration(ConfigKeys.CLAIMMESSAGE_MAX_TIMEOUT));
        thingSearchRoute = new ThingSearchRoute(proxyActor, actorSystem);

        websocketRoute = new WebsocketRoute(streamingActor,
                config.getInt(ConfigKeys.WEBSOCKET_SUBSCRIBER_BACKPRESSURE),
                config.getInt(ConfigKeys.WEBSOCKET_PUBLISHER_BACKPRESSURE),
                actorSystem.eventStream());

        supportedSchemaVersions = config.getIntList(ConfigKeys.SCHEMA_VERSIONS);

        apiAuthenticationDirective = gatewayAuthenticationDirectiveFactory.buildRestApiAuthentication();
        wsAuthenticationDirective = gatewayAuthenticationDirectiveFactory.buildWsAuthentication();

        exceptionHandler = createExceptionHandler();

        final ProtocolConfigReader protocolConfig = ProtocolConfigReader.fromRawConfig(config);
        protocolAdapterProvider = protocolConfig.loadProtocolAdapterProvider(actorSystem);

        headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();
        LOGGER.info("Using headerTranslator <{}>.", headerTranslator);

        this.customApiRoutesProvider = customApiRoutesProvider;
        this.customHeadersHandler = customHeadersHandler;
    }


    /**
     * Builds the {@code /} route.
     *
     * @return the {@code /} route.
     */
    public Route buildRoute() {
        return wrapWithRootDirectives(correlationId ->
                extractRequestContext(ctx ->
                        route(
                                statsRoute.buildStatsRoute(correlationId), // /stats
                                cachingHealthRoute.buildHealthRoute(), // /health
                                api(ctx, correlationId), // /api
                                ws(ctx, correlationId), // /ws
                                ownHealth(),
                                pathPrefixTest(PathMatchers.segment(DEVOPS_AUTH_SECURED), segment ->
                                    authenticateDevopsBasic(REALM_DEVOPS,
                                            route(
                                                    overallStatusRoute.buildStatusRoute(), // /status
                                                    devopsRoute.buildDevopsRoute(ctx) // /devops
                                            )
                                    )
                                )
                        )
                )
        );
    }

    private Route wrapWithRootDirectives(final java.util.function.Function<String, Route> rootRoute) {
        final Function<Function<String, Route>, Route> outerRouteProvider = innerRouteProvider ->
                /* the outer handleExceptions is for handling exceptions in the directives wrapping the rootRoute
                   (which normally should not occur */
                handleExceptions(exceptionHandler, () ->
                        ensureCorrelationId(correlationId ->
                                RequestTimeoutHandlingDirective.handleRequestTimeout(correlationId, () ->
                                        logRequestResult(correlationId, () ->
                                                innerRouteProvider.apply(correlationId)
                                        )
                                )
                        )
                );

        final Function<String, Route> innerRouteProvider = correlationId ->
                EncodingEnsuringDirective.ensureEncoding(correlationId, () ->
                        HttpsEnsuringDirective.ensureHttps(correlationId, () ->
                                CorsEnablingDirective.enableCors(() ->
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
                        )
                );
        return outerRouteProvider.apply(innerRouteProvider);
    }

    private Route apiAuthentication(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return apiAuthenticationDirective.authenticate(correlationId, inner);
    }

    private Route wsAuthentication(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return wsAuthenticationDirective.authenticate(correlationId, inner);
    }

    private Route ownHealth() {
        return pathPrefix("status", () -> pathPrefix("own", ownStatusRoute::buildStatusRoute));
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
                                        parameterOptional(TopicPath.Channel.LIVE.getName(), liveParam ->
                                            withDittoHeaders(
                                                    authContext,
                                                    apiVersion,
                                                    correlationId,
                                                    ctx,
                                                    liveParam.orElse(null),
                                                    CustomHeadersHandler.RequestType.API,
                                                    dittoHeaders ->
                                                            buildApiSubRoutes(ctx, dittoHeaders, authContext)
                                            )
                                        )
                            )
                    ))
                )
        );
    }

    private Route ensureSchemaVersion(final Function<Integer, Route> inner) {
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
                    } else {
                        final CommandNotSupportedException commandNotSupportedException =
                                CommandNotSupportedException.newBuilder(apiVersion).build();
                        return complete(
                                HttpResponse.create().withStatus(commandNotSupportedException.getStatusCode().toInt())
                                        .withEntity(ContentTypes.APPLICATION_JSON,
                                                ByteString.fromString(commandNotSupportedException.toJsonString())));
                    }
                });
    }

    private Route buildApiSubRoutes(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {
        final Route customApiSubRoutes = customApiRoutesProvider.authorized(dittoHeaders);

        return Directives.route(
                // /api/{apiVersion}/policies
                policiesRoute.buildPoliciesRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/things SSE support
                sseThingsRoute.buildThingsSseRoute(ctx, () ->
                        overwriteDittoHeaders(ctx, dittoHeaders, CustomHeadersHandler.RequestType.SSE, authorizationContext)),
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
                                    withDittoHeaders(authContext, wsVersion, correlationId, ctx, null,
                                            CustomHeadersHandler.RequestType.WS, dittoHeaders -> {

                                            final String userAgent = extractUserAgent(ctx).orElse(null);
                                            final ProtocolAdapter chosenProtocolAdapter =
                                                    protocolAdapterProvider.getProtocolAdapter(userAgent);
                                            return websocketRoute.buildWebsocketRoute(wsVersion, correlationId,
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

    private Route withDittoHeaders(final AuthorizationContext authorizationContext, final Integer version,
            final String correlationId, final RequestContext ctx, @Nullable final String liveParam,
            final CustomHeadersHandler.RequestType requestType, final Function<DittoHeaders, Route> inner) {

        final DittoHeaders dittoHeaders =
                buildDittoHeaders(authorizationContext, version, correlationId, ctx, liveParam, requestType);
        return inner.apply(dittoHeaders);
    }

    private DittoHeaders overwriteDittoHeaders(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final CustomHeadersHandler.RequestType requestType, final AuthorizationContext authorizationContext) {

        final String correlationId = dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());

        return handleCustomHeaders(correlationId, ctx, requestType, authorizationContext, dittoHeaders);
    }

    private DittoHeaders buildDittoHeaders(final AuthorizationContext authorizationContext, final Integer version,
            final String correlationId, final RequestContext ctx, @Nullable final String liveParam,
            final CustomHeadersHandler.RequestType requestType) {

        final DittoHeadersBuilder builder = DittoHeaders.newBuilder();

        final Map<String, String> externalHeadersMap = getFilteredExternalHeaders(ctx.getRequest());
        builder.putHeaders(externalHeadersMap);

        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());

        builder.authorizationContext(authorizationContext)
                .schemaVersion(jsonSchemaVersion)
                .correlationId(correlationId);

        authorizationContext.getFirstAuthorizationSubject().map(AuthorizationSubject::getId).ifPresent(builder::source);

        if (liveParam != null) { // once the "live" query param was set - no matter what the value was - use live
            // channel
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


    private static ExceptionHandler createExceptionHandler() {
        return ExceptionHandler.newBuilder().match(DittoRuntimeException.class, cre -> {
            final DittoHeaders dittoHeaders = cre.getDittoHeaders();
            enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId(), () ->
                    LOGGER.info("DittoRuntimeException in gateway RootRoute: {}", cre.getMessage())
            );
            return complete(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                    .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(cre.toJsonString())));
        })
                .match(JsonRuntimeException.class, jre -> {
                    final DittoJsonException dittoJsonException = new DittoJsonException(jre);
                    final DittoHeaders dittoHeaders = dittoJsonException.getDittoHeaders();
                    enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId(), () ->
                            LOGGER.info("DittoJsonException in gateway RootRoute: {}",
                                    dittoJsonException.getMessage()));
                    return complete(HttpResponse.create().withStatus(dittoJsonException.getStatusCode().toInt())
                            .withEntity(ContentTypes.APPLICATION_JSON,
                                    ByteString.fromString(dittoJsonException.toJsonString())));
                })
                .matchAny(throwable -> {
                    LOGGER.error("Unexpected RuntimeException in gateway RootRoute: {}", throwable.getMessage(),
                            throwable);
                    return complete(StatusCodes.INTERNAL_SERVER_ERROR);
                })
                .build();
    }

    private Map<String, String> getFilteredExternalHeaders(final HttpMessage httpRequest) {
        final Map<String, String> externalHeaders =
                StreamSupport.stream(httpRequest.getHeaders().spliterator(), false)
                        .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value));
        return headerTranslator.fromExternalHeaders(externalHeaders);
    }
}

