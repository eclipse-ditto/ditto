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
import static akka.http.javadsl.server.Directives.pathPrefixTest;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CorrelationIdEnsuringDirective.ensureCorrelationId;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.REALM_DEVOPS;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.REALM_HEALTH;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevopsBasicAuthenticationDirective.authenticateDevopsBasic;
import static org.eclipse.ditto.services.gateway.endpoints.directives.RequestResultLoggingDirective.logRequestResult;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.rewriteResponse;
import static org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthorizationContextVersioningDirective.mapAuthorizationContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.gateway.endpoints.directives.CorsEnablingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.EncodingEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.RequestTimeoutHandlingDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.SecurityResponseHeadersDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.dummy.DummyAuthenticationProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.DittoAuthorizationSubjectsProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.DittoPublicKeyProvider;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtSubjectIssuerConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.JwtSubjectIssuersConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt.PublicKeyProvider;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.util.ByteString;

/**
 * Builder for creating Akka HTTP routes for {@code /}.
 */
public final class RootRoute {

    static final String HTTP_PATH_API_PREFIX = "api";
    static final String WS_PATH_PREFIX = "ws";

    private static final Logger LOGGER = LoggerFactory.getLogger(RootRoute.class);

    private static final String BLOCKING_DISPATCHER_NAME = "blocking-dispatcher";

    private static final String JWT_ISSUER_GOOGLE_DOMAIN = "accounts.google.com";
    private static final String JWT_ISSUER_GOOGLE_URL = "https://accounts.google.com";
    private static final String JWK_RESOURCE_GOOGLE = "https://www.googleapis.com/oauth2/v2/certs";

    /**
     * Contains a Pattern which routes are included in devops Basic Auth secured resources.
     */
    public static final Pattern DEVOPS_AUTH_SECURED = Pattern.compile("(" +
            OverallStatusRoute.PATH_STATUS + "|" +
            CachingHealthRoute.PATH_HEALTH + "|" +
            DevOpsRoute.PATH_DEVOPS + ").*"
    );

    private final OverallStatusRoute overallStatusRoute;
    private final CachingHealthRoute cachingHealthRoute;
    private final DevOpsRoute devopsRoute;

    private final PoliciesRoute policiesRoute;
    private final SseThingsRoute sseThingsRoute;
    private final ThingsRoute thingsRoute;
    private final ThingSearchRoute thingSearchRoute;
    private final WebsocketRoute websocketRoute;
    private final GatewayAuthenticationDirective apiAuthenticationDirective;
    private final GatewayAuthenticationDirective wsAuthenticationDirective;
    private final StatsRoute statsRoute;
    private final ExceptionHandler exceptionHandler;
    private final List<Integer> supportedSchemaVersions;

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
        checkNotNull(actorSystem, "Actor System");
        checkNotNull(proxyActor, "proxyActor");

        final MessageDispatcher blockingDispatcher = actorSystem.dispatchers().lookup(BLOCKING_DISPATCHER_NAME);
        final StatusAndHealthProvider
                statusHealthProvider = DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier);

        statsRoute = new StatsRoute(proxyActor, actorSystem);
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
                DittoProtocolAdapter.newInstance(), actorSystem.eventStream());

        supportedSchemaVersions = config.getIntList(ConfigKeys.SCHEMA_VERSIONS);

        apiAuthenticationDirective =
                generateGatewayAuthenticationDirective(config, httpClient, blockingDispatcher);
        wsAuthenticationDirective = apiAuthenticationDirective;
        exceptionHandler = createExceptionHandler();
    }

    private GatewayAuthenticationDirective generateGatewayAuthenticationDirective(final Config config,
            final HttpClientFacade httpClient, final MessageDispatcher blockingDispatcher) {
        final boolean dummyAuthEnabled = config.getBoolean(ConfigKeys.AUTHENTICATION_DUMMY_ENABLED);

        final List<AuthenticationProvider> authenticationChain = new LinkedList<>();
        if (dummyAuthEnabled) {
            LOGGER.warn("Dummy authentication is enabled - Do not use this feature in production.");
            authenticationChain.add(DummyAuthenticationProvider.INSTANCE);
        }

        final JwtSubjectIssuersConfig jwtSubjectIssuersConfig = buildJwtSubjectIssuersConfig();
        final PublicKeyProvider publicKeyProvider = DittoPublicKeyProvider.of(jwtSubjectIssuersConfig, httpClient,
                config.getInt(ConfigKeys.CACHE_PUBLIC_KEYS_MAX),
                config.getDuration(ConfigKeys.CACHE_PUBLIC_KEYS_EXPIRY));
        final DittoAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoAuthorizationSubjectsProvider.of(jwtSubjectIssuersConfig);
        authenticationChain.add(
                new JwtAuthenticationDirective(blockingDispatcher, publicKeyProvider, authorizationSubjectsProvider));

        return new GatewayAuthenticationDirective(authenticationChain);
    }

    private JwtSubjectIssuersConfig buildJwtSubjectIssuersConfig() {
        final Set<JwtSubjectIssuerConfig> configItems = new HashSet<>();

        configItems.add(new JwtSubjectIssuerConfig(SubjectIssuer.GOOGLE, JWT_ISSUER_GOOGLE_DOMAIN,
                JWK_RESOURCE_GOOGLE));
        configItems.add(new JwtSubjectIssuerConfig(SubjectIssuer.GOOGLE, JWT_ISSUER_GOOGLE_URL,
                JWK_RESOURCE_GOOGLE));

        return new JwtSubjectIssuersConfig(configItems);
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
                                api(ctx, correlationId), // /api
                                ws(correlationId), // /ws
                                pathPrefixTest(PathMatchers.segment(DEVOPS_AUTH_SECURED), segment -> {
                                    final String realm = getRealmFromSegment(segment);
                                    return authenticateDevopsBasic(realm,
                                            route(
                                                    overallStatusRoute.buildStatusRoute(), // /status
                                                    cachingHealthRoute.buildHealthRoute(), // /health
                                                    devopsRoute.buildDevopsRoute(ctx) // /devops
                                            )
                                    );
                                })


                        )
                )
        );
    }

    private Route wrapWithRootDirectives(final java.util.function.Function<String, Route> rootRoute) {
        /* the outer handleExceptions is for handling exceptions in the directives wrapping the rootRoute (which
           normally should not occur */
        return handleExceptions(exceptionHandler, () ->
                ensureCorrelationId(correlationId ->
                        rewriteResponse(correlationId, () ->
                                RequestTimeoutHandlingDirective.handleRequestTimeout(correlationId, () ->
                                        logRequestResult(correlationId, () ->
                                                EncodingEnsuringDirective.ensureEncoding(correlationId, () ->
                                                        HttpsEnsuringDirective.ensureHttps(correlationId, () ->
                                                                CorsEnablingDirective.enableCors(() ->
                                                                                SecurityResponseHeadersDirective.addSecurityResponseHeaders(
                                                                                        () ->
                                                            /* handling the rejections is done by akka automatically, but if we
                                                               do it here explicitly, we are able to log the status code for the
                                                               rejection (e.g. 404 or 405) in a wrapping directive. */
                                                                                                handleRejections(
                                                                                                        RejectionHandler.defaultHandler(),
                                                                                                        () ->
                                                                    /* the inner handleExceptions is for handling exceptions
                                                                       occurring in the route route. It makes sure that the
                                                                       wrapping directives such as addSecurityResponseHeaders are
                                                                       even called in an error case in the route route. */
                                                                                                                handleExceptions(
                                                                                                                        exceptionHandler,
                                                                                                                        () -> rootRoute
                                                                                                                                .apply(
                                                                                                                                        correlationId))
                                                                                                )
                                                                                )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private Route apiAuthentication(final String correlationId,
            final java.util.function.Function<AuthorizationContext, Route> inner) {
        return apiAuthenticationDirective.authenticate(correlationId, inner);
    }

    private Route wsAuthentication(final String correlationId,
            final java.util.function.Function<AuthorizationContext, Route> inner) {
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
                        apiAuthentication(correlationId,
                                authContextWithPrefixedSubjects ->
                                        mapAuthorizationContext(
                                                correlationId,
                                                apiVersion,
                                                authContextWithPrefixedSubjects,
                                                authContext ->
                                                        extractDittoHeaders(
                                                                authContext,
                                                                apiVersion,
                                                                correlationId,
                                                                dittoHeaders ->
                                                                        buildApiSubRoutes(ctx, dittoHeaders)
                                                        )
                                        )
                        )
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

    private Route buildApiSubRoutes(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return Directives.route(
                // /api/{apiVersion}/policies
                policiesRoute.buildPoliciesRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/things SSE support
                sseThingsRoute.buildThingsSseRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/things
                thingsRoute.buildThingsRoute(ctx, dittoHeaders),
                // /api/{apiVersion}/search/things
                thingSearchRoute.buildSearchRoute(ctx, dittoHeaders)
        );
    }

    /*
     * Describes {@code /ws} route.
     *
     * @return route for Websocket resource.
     */
    private Route ws(final String correlationId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(WS_PATH_PREFIX), () -> // /ws
                ensureSchemaVersion(wsVersion -> // /ws/<wsVersion>
                        wsAuthentication(correlationId, authContextWithPrefixedSubjects ->
                                mapAuthorizationContext(correlationId, wsVersion,
                                        authContextWithPrefixedSubjects,
                                        authContext ->
                                                websocketRoute.buildWebsocketRoute(wsVersion, correlationId,
                                                        authContext)
                                )
                        )
                )
        );
    }

    private static Route extractDittoHeaders(final AuthorizationContext authorizationContext,
            final Integer version, final String correlationId,
            final java.util.function.Function<DittoHeaders, Route> inner) {
        final DittoHeaders dittoHeaders = buildDittoHeaders(authorizationContext, version, correlationId);
        return inner.apply(dittoHeaders);
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

    private static DittoHeaders buildDittoHeaders(final AuthorizationContext authorizationContext,
            final Integer version, final String correlationId) {
        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());
        final DittoHeadersBuilder builder = DittoHeaders.newBuilder()
                .authorizationContext(authorizationContext)
                .schemaVersion(jsonSchemaVersion)
                .correlationId(correlationId);

        authorizationContext.getFirstAuthorizationSubject().map(AuthorizationSubject::getId).ifPresent(builder::source);

        return builder.build();
    }

    /**
     * Computes the basic-auth realm from the path segment.
     *
     * @param segment The path segment, should match {@link this#DEVOPS_AUTH_SECURED}
     * @return Basic-auth realm for the path
     */
    private static String getRealmFromSegment(final String segment) {
        if (segment.startsWith(CachingHealthRoute.PATH_HEALTH)) {
            return REALM_HEALTH;
        } else {
            return REALM_DEVOPS;
        }
    }

}

