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
package org.eclipse.ditto.services.connectivity.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ConnectionSupervisorActor;
import org.eclipse.ditto.services.connectivity.messaging.DefaultClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ReconnectActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceOperationsActor;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.MetricsReporterConfig;
import org.eclipse.ditto.services.utils.health.config.PersistenceConfig;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetricsReporter;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;

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
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class ConnectivityRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "connectivityRoot";

    private static final String CLUSTER_ROLE = "connectivity";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                return restartChild();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);

                log.warning("Illegal Argument in child actor: {}", sw.toString());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {
                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                return restartChild();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                return restartChild();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to ConnectivityRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(JMSRuntimeException.class, e -> {
                log.warning("JMSRuntimeException '{}' occurred.", e.getMessage());
                return restartChild();
            }).match(NamingException.class, e -> {
                log.warning("NamingException '{}' occurred.", e.getMessage());
                return restartChild();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    @SuppressWarnings("unused")
    private ConnectivityRootActor(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        final ClusterConfig clusterConfig = connectivityConfig.getClusterConfig();
        final ActorSystem actorSystem = getContext().system();

        final ActorRef conciergeForwarder =
                getConciergeForwarder(clusterConfig, pubSubMediator, conciergeForwarderSignalTransformer);
        final DittoProtocolSub dittoProtocolSub = DittoProtocolSub.of(actorSystem);
        final Props connectionSupervisorProps =
                getConnectionSupervisorProps(dittoProtocolSub, conciergeForwarder, commandValidator);

        // Create persistence streaming actor (with no cache) and make it known to pubSubMediator.
        final ActorRef persistenceStreamingActor =
                startChildActor(ConnectionPersistenceStreamingActorCreator.ACTOR_NAME,
                        ConnectionPersistenceStreamingActorCreator.props(0));
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());

        startClusterSingletonActor(
                ReconnectActor.props(getConnectionShardRegion(actorSystem, connectionSupervisorProps, clusterConfig),
                        MongoReadJournal.newInstance(actorSystem)));

        startChildActor(ConnectionPersistenceOperationsActor.ACTOR_NAME,
                ConnectionPersistenceOperationsActor.props(pubSubMediator, connectivityConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), connectivityConfig.getPersistenceOperationsConfig()));

        final CompletionStage<ServerBinding> binding =
                getHttpBinding(connectivityConfig.getHttpConfig(), actorSystem, materializer,
                        getHealthCheckingActor(connectivityConfig, pubSubMediator));
        binding.thenAccept(theBinding -> CoordinatedShutdown.get(actorSystem).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint ...");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error("Something very bad happened! " + failure.getMessage(), failure);
            actorSystem.terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @param commandValidator custom command validator for connectivity commands
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer,
            final ConnectivityCommandInterceptor commandValidator) {

        return Props.create(ConnectivityRootActor.class, connectivityConfig, pubSubMediator, materializer,
                conciergeForwarderSignalTransformer, commandValidator);
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer) {

        return Props.create(ConnectivityRootActor.class, connectivityConfig, pubSubMediator, materializer,
                conciergeForwarderSignalTransformer, null);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private SupervisorStrategy.Directive restartChild() {
        log.info("Restarting child ...");
        return SupervisorStrategy.restart();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void startClusterSingletonActor(final Props props) {
        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, ReconnectActor.ACTOR_NAME, props);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

    private ActorRef getHealthCheckingActor(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator) {
        final HealthCheckConfig healthCheckConfig = connectivityConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        final PersistenceConfig persistenceConfig = healthCheckConfig.getPersistenceConfig();
        if (persistenceConfig.isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }
        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();

        final MetricsReporterConfig metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                        MongoHealthChecker.props(),
                        MongoMetricsReporter.props(
                                metricsReporterConfig.getResolution(),
                                metricsReporterConfig.getHistory(),
                                pubSubMediator
                        )
                ));
    }

    private ActorRef getConciergeForwarder(final ClusterConfig clusterConfig, final ActorRef pubSubMediator,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer) {

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(getContext(),
                        clusterConfig.getNumberOfShards());

        return startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter,
                        conciergeForwarderSignalTransformer));
    }

    private static Props getConnectionSupervisorProps(final DittoProtocolSub dittoProtocolSub,
            final ActorRef conciergeForwarder,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        final ClientActorPropsFactory clientActorPropsFactory =
                DefaultClientActorPropsFactory.getInstance();

        return ConnectionSupervisorActor.props(dittoProtocolSub, conciergeForwarder,
                clientActorPropsFactory, commandValidator);
    }

    private static ActorRef getConnectionShardRegion(final ActorSystem actorSystem,
            final Props connectionSupervisorProps, final ClusterConfig clusterConfig) {

        final ClusterShardingSettings shardingSettings = ClusterShardingSettings.create(actorSystem)
                .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE);

        return ClusterSharding.get(actorSystem)
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));
    }

    private CompletionStage<ServerBinding> getHttpBinding(final HttpConfig httpConfig,
            final ActorSystem actorSystem,
            final ActorMaterializer materializer,
            final ActorRef healthCheckingActor) {

        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }

        return Http.get(actorSystem)
                .bindAndHandle(createRoute(actorSystem, healthCheckingActor).flow(actorSystem, materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);
    }

}
