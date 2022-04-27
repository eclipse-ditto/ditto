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
package org.eclipse.ditto.gateway.service.starter;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.edge.api.dispatching.ConciergeForwarderActor;
import org.eclipse.ditto.edge.api.dispatching.ShardRegions;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirectiveFactory;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.gateway.service.endpoints.routes.CustomApiRoutesProvider;
import org.eclipse.ditto.gateway.service.endpoints.routes.RootRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.endpoints.routes.cloudevents.CloudEventsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.OAuthTokenIntegrationSubjectIdFactory;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.sse.ThingsSseRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiRoute;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewayByRoundTripSignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewayCachingSignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.gateway.service.health.GatewayHttpReadinessCheck;
import org.eclipse.ditto.gateway.service.health.StatusAndHealthProvider;
import org.eclipse.ditto.gateway.service.proxy.actors.AbstractProxyActor;
import org.eclipse.ditto.gateway.service.proxy.actors.ProxyActor;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamingActor;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.health.routes.StatusRoute;
import org.eclipse.ditto.internal.utils.http.DefaultHttpClientFacade;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.pubsub.DittoProtocolSub;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;

/**
 * The Root Actor of the API Gateway's Akka ActorSystem.
 */
final class GatewayRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "gatewayRoot";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final CompletionStage<ServerBinding> httpBinding;

    @SuppressWarnings("unused")
    private GatewayRootActor(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator) {

        final ActorSystem actorSystem = context().system();
        final var clusterConfig = gatewayConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();
        final AuthenticationConfig authenticationConfig = gatewayConfig.getAuthenticationConfig();
        final CacheConfig publicKeysConfig = gatewayConfig.getCachesConfig().getPublicKeysConfig();
        final HealthCheckConfig healthCheckConfig = gatewayConfig.getHealthCheckConfig();
        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();

        final ShardRegions shardRegions = ShardRegions.of(actorSystem, clusterConfig);
        final var conciergeForwarder = startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, shardRegions));
        final var proxyActor = startProxyActor(actorSystem, pubSubMediator, conciergeForwarder);

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        final DittoProtocolSub dittoProtocolSub = DittoProtocolSub.get(actorSystem);

        final DefaultHttpClientFacade httpClient =
                DefaultHttpClientFacade.getInstance(actorSystem, authenticationConfig.getHttpProxyConfig());

        final JwtAuthenticationFactory jwtAuthenticationFactory =
                getJwtAuthenticationFactory(httpClient, publicKeysConfig, authenticationConfig, actorSystem);

        final JwtAuthenticationResultProvider jwtAuthenticationResultProvider =
                JwtAuthenticationResultProvider.get(actorSystem);

        final DevopsAuthenticationDirectiveFactory devopsAuthenticationDirectiveFactory =
                getDevopsAuthenticationDirectiveFactory(httpClient, publicKeysConfig, authenticationConfig,
                        actorSystem);

        final ProtocolAdapterProvider protocolAdapterProvider =
                ProtocolAdapterProvider.load(gatewayConfig.getProtocolConfig(), actorSystem);
        final HeaderTranslator headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();

        final ActorRef streamingActor = startChildActor(StreamingActor.ACTOR_NAME,
                StreamingActor.props(dittoProtocolSub,
                        proxyActor,
                        jwtAuthenticationFactory.getJwtValidator(),
                        jwtAuthenticationResultProvider,
                        gatewayConfig.getStreamingConfig(),
                        headerTranslator,
                        pubSubMediator,
                        conciergeForwarder));

        final ActorRef healthCheckActor = createHealthCheckActor(healthCheckConfig);
        final var hostname = getHostname(httpConfig);

        final Route rootRoute = createRoute(actorSystem, gatewayConfig, proxyActor, streamingActor,
                healthCheckActor, pubSubMediator, healthCheckConfig, jwtAuthenticationFactory,
                devopsAuthenticationDirectiveFactory, protocolAdapterProvider, headerTranslator);
        final Route routeWithLogging = Directives.logRequest("http", Logging.DebugLevel(), () -> rootRoute);

        httpBinding = Http.get(actorSystem)
                .newServerAt(hostname, httpConfig.getPort())
                .bindFlow(routeWithLogging.flow(actorSystem))
                .thenApply(theBinding -> {
                    log.info("Serving HTTP requests on port <{}> ...", theBinding.localAddress().getPort());
                    return theBinding.addToCoordinatedShutdown(httpConfig.getCoordinatedShutdownTimeout(), actorSystem);
                })
                .exceptionally(failure -> {
                    log.error(failure,
                            "Could not create the server binding for the Gateway route because of: <{}>",
                            failure.getMessage());
                    log.error("Terminating the actor system");
                    actorSystem.terminate();
                    return null;
                });
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param gatewayConfig the configuration settings of this service.
     * @param pubSubMediator the pub-sub mediator.
     * @return the Akka configuration Props object.
     */
    static Props props(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator) {
        return Props.create(GatewayRootActor.class, gatewayConfig, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE, msg -> {
                    final var sender = getSender();
                    httpBinding.thenAccept(
                            binding -> sender.tell(GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE_RESPONSE,
                                    ActorRef.noSender()));
                })
                .build()
                .orElse(super.createReceive());
    }

    private static Route createRoute(final ActorSystem actorSystem,
            final GatewayConfig gatewayConfig,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final ActorRef pubSubMediator,
            final HealthCheckConfig healthCheckConfig,
            final JwtAuthenticationFactory jwtAuthenticationFactory,
            final DevopsAuthenticationDirectiveFactory devopsAuthenticationDirectiveFactory,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final HeaderTranslator headerTranslator) {

        final AuthenticationConfig authConfig = gatewayConfig.getAuthenticationConfig();
        final Materializer materializer = SystemMaterializer.get(actorSystem).materializer();

        final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory =
                GatewayAuthenticationDirectiveFactory.get(actorSystem);

        final DevopsAuthenticationDirective devopsAuthenticationDirective =
                devopsAuthenticationDirectiveFactory.devops();
        final DevopsAuthenticationDirective statusAuthenticationDirective =
                devopsAuthenticationDirectiveFactory.status();

        final Supplier<ClusterStatus> clusterStateSupplier = new ClusterStatusSupplier(Cluster.get(actorSystem));
        final StatusAndHealthProvider statusAndHealthProvider =
                DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier, healthCheckConfig);

        final LimitsConfig limitsConfig = gatewayConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();

        final var streamingConfig = gatewayConfig.getStreamingConfig();
        final var signalEnrichmentConfig = streamingConfig.getSignalEnrichmentConfig();
        final var signalEnrichmentProvider = signalEnrichmentProvider(signalEnrichmentConfig, actorSystem);

        final var commandConfig = gatewayConfig.getCommandConfig();

        final var connectivityShardRegionProxyActor =
                getConnectivityShardRegionProxyActor(actorSystem, gatewayConfig.getClusterConfig());

        final var routeBaseProperties = RouteBaseProperties.newBuilder()
                .actorSystem(actorSystem)
                .proxyActor(proxyActor)
                .httpConfig(httpConfig)
                .commandConfig(commandConfig)
                .headerTranslator(headerTranslator)
                .connectivityShardRegionProxy(connectivityShardRegionProxyActor)
                .build();

        final var customApiRoutesProvider = CustomApiRoutesProvider.get(actorSystem);

        return RootRoute.getBuilder(httpConfig)
                .statsRoute(new StatsRoute(routeBaseProperties, devopsAuthenticationDirective))
                .statusRoute(new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem))
                .overallStatusRoute(new OverallStatusRoute(clusterStateSupplier,
                        statusAndHealthProvider,
                        statusAuthenticationDirective))
                .cachingHealthRoute(new CachingHealthRoute(statusAndHealthProvider,
                        gatewayConfig.getPublicHealthConfig()))
                .devopsRoute(new DevOpsRoute(routeBaseProperties, devopsAuthenticationDirective))
                .policiesRoute(new PoliciesRoute(routeBaseProperties,
                        OAuthTokenIntegrationSubjectIdFactory.of(authConfig.getOAuthConfig())))
                .sseThingsRoute(ThingsSseRouteBuilder.getInstance(actorSystem, streamingActor, streamingConfig, pubSubMediator)
                        .withProxyActor(proxyActor)
                        .withSignalEnrichmentProvider(signalEnrichmentProvider))
                .thingsRoute(new ThingsRoute(routeBaseProperties,
                        gatewayConfig.getMessageConfig(),
                        gatewayConfig.getClaimMessageConfig()))
                .thingSearchRoute(new ThingSearchRoute(routeBaseProperties))
                .whoamiRoute(new WhoamiRoute(routeBaseProperties))
                .cloudEventsRoute(new CloudEventsRoute(routeBaseProperties, gatewayConfig.getCloudEventsConfig()))
                .websocketRoute(WebSocketRoute.getInstance(actorSystem, streamingActor, streamingConfig, materializer)
                        .withSignalEnrichmentProvider(signalEnrichmentProvider)
                        .withHeaderTranslator(headerTranslator))
                .supportedSchemaVersions(httpConfig.getSupportedSchemaVersions())
                .protocolAdapterProvider(protocolAdapterProvider)
                .headerTranslator(headerTranslator)
                .httpAuthenticationDirective(
                        authenticationDirectiveFactory.buildHttpAuthentication(jwtAuthenticationFactory))
                .wsAuthenticationDirective(
                        authenticationDirectiveFactory.buildWsAuthentication(jwtAuthenticationFactory))
                .dittoHeadersSizeChecker(dittoHeadersSizeChecker)
                .customApiRoutesProvider(customApiRoutesProvider, routeBaseProperties)
                .build();
    }

    private static GatewaySignalEnrichmentProvider signalEnrichmentProvider(
            final GatewaySignalEnrichmentConfig signalEnrichmentConfig, final ActorSystem actorSystem) {

        if (signalEnrichmentConfig.isCachingEnabled()) {
            return new GatewayCachingSignalEnrichmentProvider(actorSystem, signalEnrichmentConfig);
        } else {
            return new GatewayByRoundTripSignalEnrichmentProvider(actorSystem, signalEnrichmentConfig);
        }
    }

    private ActorRef createHealthCheckActor(final HealthCheckConfig healthCheckConfig) {
        final HealthCheckingActorOptions healthCheckingActorOptions =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval())
                        .build();

        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, null));
    }

    private static ActorRef getConnectivityShardRegionProxyActor(final ActorSystem actorSystem,
            final ClusterConfig clusterConfig) {

        final var shardRegionProxyActorFactory = ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);
        return shardRegionProxyActorFactory.getShardRegionProxyActor(ConnectivityMessagingConstants.CLUSTER_ROLE,
                ConnectivityMessagingConstants.SHARD_REGION);
    }

    private ActorRef startProxyActor(final ActorRefFactory actorSystem, final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder) {

        final ActorSelection devOpsCommandsActor =
                actorSystem.actorSelection(DevOpsRoute.DEVOPS_COMMANDS_ACTOR_SELECTION);

        return startChildActor(AbstractProxyActor.ACTOR_NAME,
                ProxyActor.props(pubSubMediator, devOpsCommandsActor, conciergeForwarder));

    }

    private static JwtAuthenticationFactory getJwtAuthenticationFactory(final HttpClientFacade httpClient,
            final CacheConfig publicKeysConfig,
            final AuthenticationConfig authenticationConfig,
            final ActorSystem actorSystem) {

        final OAuthConfig oAuthConfig = authenticationConfig.getOAuthConfig();
        return JwtAuthenticationFactory.newInstance(oAuthConfig, publicKeysConfig, httpClient, actorSystem);
    }

    private static DevopsAuthenticationDirectiveFactory getDevopsAuthenticationDirectiveFactory(
            final HttpClientFacade httpClient,
            final CacheConfig publicKeysConfig,
            final AuthenticationConfig authenticationConfig,
            final ActorSystem actorSystem) {

        final OAuthConfig devopsOauthConfig = authenticationConfig.getDevOpsConfig().getOAuthConfig();
        final JwtAuthenticationFactory devopsJwtAuthenticationFactory =
                JwtAuthenticationFactory.newInstance(devopsOauthConfig, publicKeysConfig, httpClient, actorSystem);
        return DevopsAuthenticationDirectiveFactory.newInstance(devopsJwtAuthenticationFactory,
                authenticationConfig.getDevOpsConfig());
    }

    private String getHostname(final org.eclipse.ditto.base.service.config.http.HttpConfig httpConfig) {
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }
        return hostname;
    }

}
