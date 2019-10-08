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

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DevOpsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.OAuthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.WebSocketConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.DittoGatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.RootRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.DefaultHttpClientFacade;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.GatewayHttpReadinessCheck;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.gateway.proxy.actors.ProxyActor;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DefaultJwtAuthorizationContextProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DittoJwtAuthorizationSubjectsProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.DittoPublicKeyProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthorizationContextProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuerConfig;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtSubjectIssuersConfig;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.PublicKeyProvider;
import org.eclipse.ditto.services.gateway.starter.config.GatewayConfig;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
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
import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.dispatch.MessageDispatcher;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;

/**
 * The Root Actor of the API Gateway's Akka ActorSystem.
 */
final class GatewayRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "gatewayRoot";

    private static final String AUTHENTICATION_DISPATCHER_NAME = "authentication-dispatcher";

    private static final String CHILD_RESTART_INFO_MSG = "Restarting child ...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(CHILD_RESTART_INFO_MSG);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {
                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info(CHILD_RESTART_INFO_MSG);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException <{}> should not be escalated to GatewayRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(CHILD_RESTART_INFO_MSG);
                return SupervisorStrategy.restart();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message: '{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final CompletionStage<ServerBinding> httpBinding;

    @SuppressWarnings("unused")
    private GatewayRootActor(final GatewayConfig gatewayConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        final ActorSystem actorSystem = context().system();

        final AuthenticationConfig authConfig = gatewayConfig.getAuthenticationConfig();
        final DefaultHttpClientFacade httpClient =
                DefaultHttpClientFacade.getInstance(actorSystem, authConfig.getHttpProxyConfig());

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

        final ActorRef proxyActor = startChildActor(ProxyActor.ACTOR_NAME,
                ProxyActor.props(pubSubMediator, devOpsCommandsActor, conciergeForwarder));

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        final DittoProtocolSub dittoProtocolSub = DittoProtocolSub.of(getContext());

        final JwtSubjectIssuersConfig jwtSubjectIssuersConfig =
                buildJwtSubjectIssuersConfig(gatewayConfig.getAuthenticationConfig().getOAuthConfig());

        final PublicKeyProvider publicKeyProvider = DittoPublicKeyProvider.of(jwtSubjectIssuersConfig, httpClient,
                gatewayConfig.getCachesConfig().getPublicKeysConfig(), "ditto_authorization_jwt_publicKeys_cache");

        final JwtValidator jwtValidator = JwtValidator.getInstance(publicKeyProvider);

        final DittoJwtAuthorizationSubjectsProvider authorizationSubjectsProvider =
                DittoJwtAuthorizationSubjectsProvider.of(jwtSubjectIssuersConfig);
        final DefaultJwtAuthorizationContextProvider authorizationContextProvider =
                DefaultJwtAuthorizationContextProvider.getInstance(authorizationSubjectsProvider);

        final ActorRef streamingActor = startChildActor(StreamingActor.ACTOR_NAME,
                StreamingActor.props(dittoProtocolSub, proxyActor, jwtValidator, authorizationContextProvider));

        final HealthCheckConfig healthCheckConfig = gatewayConfig.getHealthCheckConfig();
        final ActorRef healthCheckActor = createHealthCheckActor(healthCheckConfig);

        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }

        final Route rootRoute = createRoute(actorSystem, gatewayConfig, proxyActor, streamingActor, healthCheckActor,
                healthCheckConfig, jwtValidator, authorizationContextProvider);
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
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchEquals(GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE, msg -> {
                    final ActorRef sender = getSender();
                    httpBinding.thenAccept(binding -> sender.tell(
                            GatewayHttpReadinessCheck.READINESS_ASK_MESSAGE_RESPONSE, ActorRef.noSender()));
                })
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem,
            final GatewayConfig gatewayConfig,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final HealthCheckConfig healthCheckConfig,
            final JwtValidator jwtValidator,
            final JwtAuthorizationContextProvider authorizationContextProvider) {

        final AuthenticationConfig authConfig = gatewayConfig.getAuthenticationConfig();

        final MessageDispatcher authenticationDispatcher =
                actorSystem.dispatchers().lookup(AUTHENTICATION_DISPATCHER_NAME);

        final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory =
                new DittoGatewayAuthenticationDirectiveFactory(authConfig, jwtValidator,
                        authorizationContextProvider, authenticationDispatcher);

        final ProtocolAdapterProvider protocolAdapterProvider =
                ProtocolAdapterProvider.load(gatewayConfig.getProtocolConfig(), actorSystem);
        final HeaderTranslator headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();

        final WebSocketConfig webSocketConfig = gatewayConfig.getWebSocketConfig();

        final Supplier<ClusterStatus> clusterStateSupplier = new ClusterStatusSupplier(Cluster.get(actorSystem));
        final StatusAndHealthProvider statusAndHealthProvider =
                DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier, healthCheckConfig);

        final LimitsConfig limitsConfig = gatewayConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        final HttpConfig httpConfig = gatewayConfig.getHttpConfig();
        final DevOpsConfig devOpsConfig = authConfig.getDevOpsConfig();

        return RootRoute.getBuilder(httpConfig)
                .statsRoute(new StatsRoute(proxyActor, actorSystem, httpConfig, devOpsConfig, headerTranslator))
                .statusRoute(new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem))
                .overallStatusRoute(new OverallStatusRoute(clusterStateSupplier, statusAndHealthProvider, devOpsConfig))
                .cachingHealthRoute(
                        new CachingHealthRoute(statusAndHealthProvider, gatewayConfig.getPublicHealthConfig()))
                .devopsRoute(new DevOpsRoute(proxyActor, actorSystem, httpConfig, devOpsConfig, headerTranslator))
                .policiesRoute(new PoliciesRoute(proxyActor, actorSystem, httpConfig, headerTranslator))
                .sseThingsRoute(
                        new SseThingsRoute(proxyActor, actorSystem, httpConfig, streamingActor, headerTranslator))
                .thingsRoute(new ThingsRoute(proxyActor, actorSystem, gatewayConfig.getMessageConfig(),
                        gatewayConfig.getClaimMessageConfig(), httpConfig, headerTranslator))
                .thingSearchRoute(new ThingSearchRoute(proxyActor, actorSystem, httpConfig, headerTranslator))
                .websocketRoute(new WebsocketRoute(streamingActor, webSocketConfig, actorSystem.eventStream()))
                .supportedSchemaVersions(httpConfig.getSupportedSchemaVersions())
                .protocolAdapterProvider(protocolAdapterProvider)
                .headerTranslator(headerTranslator)
                .httpAuthenticationDirective(authenticationDirectiveFactory.buildHttpAuthentication())
                .wsAuthenticationDirective(authenticationDirectiveFactory.buildWsAuthentication())
                .dittoHeadersSizeChecker(dittoHeadersSizeChecker)
                .build();
    }

    private ActorRef createHealthCheckActor(final HealthCheckConfig healthCheckConfig) {
        final HealthCheckingActorOptions healthCheckingActorOptions =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval())
                        .build();

        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, null));
    }

    private static JwtSubjectIssuersConfig buildJwtSubjectIssuersConfig(final OAuthConfig config) {
        final Set<JwtSubjectIssuerConfig> configItems = config.getOpenIdConnectIssuers().entrySet().stream()
                .map(entry -> new JwtSubjectIssuerConfig(entry.getValue(), entry.getKey()))
                .collect(Collectors.toSet());

        return new JwtSubjectIssuersConfig(configItems);
    }

}
