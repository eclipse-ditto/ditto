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
package org.eclipse.ditto.services.concierge.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.base.actors.DittoRootActor;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.actors.cleanup.EventSnapshotCleanupCoordinator;
import org.eclipse.ditto.services.concierge.common.ConciergeConfig;
import org.eclipse.ditto.services.concierge.starter.proxy.EnforcerActorFactory;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.SingletonStatusReporter;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.PersistenceConfig;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;

/**
 * The root actor of the concierge service.
 */
public final class ConciergeRootActor extends DittoRootActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeRoot";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @SuppressWarnings("unused")
    private <C extends ConciergeConfig> ConciergeRootActor(final C conciergeConfig,
            final ActorRef pubSubMediator,
            final EnforcerActorFactory<C> enforcerActorFactory,
            final ActorMaterializer materializer) {

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        final ActorContext context = getContext();
        final ShardRegions shardRegions = ShardRegions.of(getContext().getSystem(), conciergeConfig.getClusterConfig());

        enforcerActorFactory.startEnforcerActor(context, conciergeConfig, pubSubMediator, shardRegions);

        final ActorRef conciergeForwarder = context.findChild(ConciergeForwarderActor.ACTOR_NAME).orElseThrow(() ->
                new IllegalStateException("ConciergeForwarder could not be found"));

        final ActorRef cleanupCoordinator = startClusterSingletonActor(
                EventSnapshotCleanupCoordinator.ACTOR_NAME,
                EventSnapshotCleanupCoordinator.props(conciergeConfig.getPersistenceCleanupConfig(), pubSubMediator,
                        shardRegions));

        final ActorRef healthCheckingActor = startHealthCheckingActor(conciergeConfig, cleanupCoordinator);

        bindHttpStatusRoute(healthCheckingActor, conciergeConfig.getHttpConfig(), materializer);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param conciergeConfig the config of Concierge.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param enforcerActorFactory factory for creating sharded enforcer actors.
     * @param materializer the materializer for the Akka actor system.
     * @return the Akka configuration Props object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <C extends ConciergeConfig> Props props(final C conciergeConfig,
            final ActorRef pubSubMediator,
            final EnforcerActorFactory<C> enforcerActorFactory,
            final ActorMaterializer materializer) {

        checkNotNull(conciergeConfig, "config of Concierge");
        checkNotNull(pubSubMediator, "pub-sub mediator");
        checkNotNull(enforcerActorFactory, "EnforcerActor factory");
        checkNotNull(materializer, "ActorMaterializer");

        return Props.create(ConciergeRootActor.class, conciergeConfig, pubSubMediator, enforcerActorFactory,
                materializer);
    }


    private ActorRef startClusterSingletonActor(final String actorName, final Props props) {

        return ClusterUtil.startSingleton(getContext(), ConciergeMessagingConstants.CLUSTER_ROLE, actorName, props);
    }

    private ActorRef startHealthCheckingActor(final ConciergeConfig conciergeConfig,
            final ActorRef cleanupCoordinator) {

        final HealthCheckConfig healthCheckConfig = conciergeConfig.getHealthCheckConfig();

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());

        final PersistenceConfig persistenceConfig = healthCheckConfig.getPersistenceConfig();
        if (persistenceConfig.isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }
        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();

        final ActorRef cleanupCoordinatorProxy = ClusterUtil.startSingletonProxy(getContext(),
                ConciergeMessagingConstants.CLUSTER_ROLE, cleanupCoordinator);

        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                        MongoHealthChecker.props(),
                        SingletonStatusReporter.props(ConciergeMessagingConstants.CLUSTER_ROLE,
                                cleanupCoordinatorProxy))
        );
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () ->
                logResult("http-response", statusRoute::buildStatusRoute));
    }

    private void bindHttpStatusRoute(final ActorRef healthCheckingActor, final HttpConfig httpConfig,
            final ActorMaterializer materializer) {

        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system())
                .bindAndHandle(createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                        materializer), ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint..");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            getContext().system().terminate();
            return null;
        });
    }

}
