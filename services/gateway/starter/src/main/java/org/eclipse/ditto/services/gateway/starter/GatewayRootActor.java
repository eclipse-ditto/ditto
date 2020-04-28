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
package org.eclipse.ditto.services.gateway.starter;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.base.actors.DittoRootActor;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.DittoGatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.RootRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.ThingsSseRouteBuilder;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebSocketRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewayByRoundTripSignalEnrichmentProvider;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewayCachingSignalEnrichmentProvider;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.GatewayHttpReadinessCheck;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.proxy.actors.AbstractProxyActor;
import org.eclipse.ditto.services.gateway.proxy.actors.ProxyActor;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.services.gateway.security.utils.DefaultHttpClientFacade;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.gateway.util.config.GatewayConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.gateway.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.services.gateway.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.util.config.security.DevOpsConfig;
import org.eclipse.ditto.services.gateway.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.services.gateway.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.dispatch.MessageDispatcher;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;

/**
 * The Root Actor of the API Gateway's Akka ActorSystem.
 */
final class GatewayRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "gatewayRoot";

    private static final String AUTHENTICATION_DISPATCHER_NAME = "authentication-dispatcher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final CompletionStage<ServerBinding> httpBinding;

    @SuppressWarnings("unused")
    private GatewayRootActor(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        final ActorSystem actorSystem = context().system();

        final ClusterConfig clusterConfig = gatewayConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();

        log.info("Starting /user/{}", DevOpsCommandsActor.ACTOR_NAME);
        final ActorRef devOpsCommandsActor = actorSystem.actorOf(
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), GatewayService.SERVICE_NAME,
                        InstanceIdentifierSupplier.getInstance().get()),
                DevOpsCommandsActor.ACTOR_NAME);

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(getContext(),
                        numberOfShards);

        final ActorRef conciergeForwarder = startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter));

        final ActorRef proxyActor = startChildActor(AbstractProxyActor.ACTOR_NAME,
                ProxyActor.props(pubSubMediator, devOpsCommandsActor, conciergeForwarder));

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        final DittoProtocolSub dittoProtocolSub = DittoProtocolSub.of(getContext());

        final AuthenticationConfig authenticationConfig = gatewayConfig.getAuthenticationConfig();
        final DefaultHttpClientFacade httpClient =
                DefaultHttpClientFacade.getInstance(actorSystem, authenticationConfig.getHttpProxyConfig());

        final JwtAuthenticationFactory jwtAuthenticationFactory =
                JwtAuthenticationFactory.newInstance(authenticationConfig.getOAuthConfig(),
                        gatewayConfig.getCachesConfig().getPublicKeysConfig(), httpClient);

        final ProtocolAdapterProvider protocolAdapterProvider =
                ProtocolAdapterProvider.load(gatewayConfig.getProtocolConfig(), actorSystem);
        final HeaderTranslator headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();

        final ActorRef streamingActor = startChildActor(StreamingActor.ACTOR_NAME,
                StreamingActor.props(dittoProtocolSub, proxyActor, jwtAuthenticationFactory,
                        gatewayConfig.getStreamingConfig(), headerTranslator, pubSubMediator, conciergeForwarder));

        final HealthCheckConfig healthCheckConfig = gatewayConfig.getHealthCheckConfig();
        final ActorRef healthCheckActor = createHealthCheckActor(healthCheckConfig);

        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }

        final Route rootRoute = createRoute(actorSystem, gatewayConfig, proxyActor, streamingActor,
                healthCheckActor, pubSubMediator, healthCheckConfig, jwtAuthenticationFactory, protocolAdapterProvider,
                headerTranslator);
        final Route routeWithLogging = Directives.logRequest("http", Logging.DebugLevel(), () -> rootRoute);

        httpBinding = Http.get(actorSystem)
                .bindAndHandle(routeWithLogging.flow(actorSystem, materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        httpBinding.thenAccept(theBinding -> {
                    log.info("Serving HTTP requests on port <{}> ...", theBinding.localAddress().getPort());
                    CoordinatedShutdown.get(actorSystem).addTask(
                            CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_http_endpoint", () -> {
                                log.info("Gracefully shutting down user HTTP endpoint ...");
                                return theBinding.terminate(Duration.ofSeconds(10))
                                        .handle((httpTerminated, e) -> Done.getInstance());
                            });
                }
        ).exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            actorSystem.terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param gatewayConfig the configuration settings of this service.
     * @param pubSubMediator the pub-sub mediator.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    static Props props(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return Props.create(GatewayRootActor.class, gatewayConfig, pubSubMediator, materializer);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE, msg -> {
                    final ActorRef sender = getSender();
                    httpBinding.thenAccept(binding -> sender.tell(
                            GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE_RESPONSE, ActorRef.noSender()));
                }).build().orElse(super.createReceive());
    }

    private static Route createRoute(final ActorSystem actorSystem,
            final GatewayConfig gatewayConfig,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final ActorRef pubSubMediator,
            final HealthCheckConfig healthCheckConfig,
            final JwtAuthenticationFactory jwtAuthenticationFactory,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final HeaderTranslator headerTranslator) {

        final AuthenticationConfig authConfig = gatewayConfig.getAuthenticationConfig();

        final MessageDispatcher authenticationDispatcher =
                actorSystem.dispatchers().lookup(AUTHENTICATION_DISPATCHER_NAME);

        final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory =
                new DittoGatewayAuthenticationDirectiveFactory(authConfig, jwtAuthenticationFactory,
                        authenticationDispatcher);

        final Supplier<ClusterStatus> clusterStateSupplier = new ClusterStatusSupplier(Cluster.get(actorSystem));
        final StatusAndHealthProvider statusAndHealthProvider =
                DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier, healthCheckConfig);

        final LimitsConfig limitsConfig = gatewayConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();
        final DevOpsConfig devOpsConfig = authConfig.getDevOpsConfig();

        final GatewaySignalEnrichmentConfig signalEnrichmentConfig =
                gatewayConfig.getStreamingConfig().getSignalEnrichmentConfig();
        final GatewaySignalEnrichmentProvider signalEnrichmentProvider =
                signalEnrichmentProvider(signalEnrichmentConfig, actorSystem);

        final StreamingConfig streamingConfig = gatewayConfig.getStreamingConfig();
        final CommandConfig commandConfig = gatewayConfig.getCommandConfig();

        return RootRoute.getBuilder(httpConfig)
                .statsRoute(new StatsRoute(proxyActor, actorSystem, httpConfig, commandConfig, devOpsConfig,
                        headerTranslator))
                .statusRoute(new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem))
                .overallStatusRoute(new OverallStatusRoute(clusterStateSupplier, statusAndHealthProvider, devOpsConfig))
                .cachingHealthRoute(
                        new CachingHealthRoute(statusAndHealthProvider, gatewayConfig.getPublicHealthConfig()))
                .devopsRoute(new DevOpsRoute(proxyActor, actorSystem, httpConfig, commandConfig, devOpsConfig,
                        headerTranslator))
                .policiesRoute(new PoliciesRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator))
                .sseThingsRoute(ThingsSseRouteBuilder.getInstance(streamingActor, streamingConfig, pubSubMediator)
                        .withSignalEnrichmentProvider(signalEnrichmentProvider))
                .thingsRoute(new ThingsRoute(proxyActor, actorSystem, httpConfig, commandConfig,
                        gatewayConfig.getMessageConfig(), gatewayConfig.getClaimMessageConfig(), headerTranslator))
                .thingSearchRoute(
                        new ThingSearchRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator))
                .websocketRoute(WebSocketRoute.getInstance(streamingActor, streamingConfig)
                        .withSignalEnrichmentProvider(signalEnrichmentProvider)
                        .withHeaderTranslator(headerTranslator))
                .supportedSchemaVersions(httpConfig.getSupportedSchemaVersions())
                .protocolAdapterProvider(protocolAdapterProvider)
                .headerTranslator(headerTranslator)
                .httpAuthenticationDirective(authenticationDirectiveFactory.buildHttpAuthentication())
                .wsAuthenticationDirective(authenticationDirectiveFactory.buildWsAuthentication())
                .dittoHeadersSizeChecker(dittoHeadersSizeChecker)
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

}
