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
package org.eclipse.ditto.services.things.starter;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.services.models.things.ThingsMessagingConstants.CLUSTER_ROLE;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.things.common.config.ThingsConfig;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceOperationsActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingsPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.services.utils.persistence.mongo.config.TagsConfig;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

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
import akka.cluster.pubsub.DistributedPubSubMediator;
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
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class ThingsRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsRoot";

    private static final String RESTARTING_CHILD_MESSAGE = "Restarting child ...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e ->
            {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e ->
            {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {

                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e ->
            {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e ->
            {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e ->
            {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e ->
            {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e ->
            {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e ->
            {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e ->
            {
                log.error(e,
                        "DittoRuntimeException <{}> should not be escalated to ThingsRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e ->
            {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e ->
            {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    @SuppressWarnings("unused")
    private ThingsRootActor(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        final ActorSystem actorSystem = getContext().system();

        final ClusterConfig clusterConfig = thingsConfig.getClusterConfig();
        final ActorRef thingsShardRegion = ClusterSharding.get(actorSystem)
                .start(ThingsMessagingConstants.SHARD_REGION,
                        getThingSupervisorActorProps(pubSubMediator, thingSnapshotterCreate),
                        ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE),
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));

        // TODO Fix compilation error.
        startChildActor(ThingPersistenceOperationsActor.ACTOR_NAME,
                ThingPersistenceOperationsActor.props(pubSubMediator, config));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(thingsShardRegion,
                ThingsMessagingConstants.SHARD_REGION, log);

        final HealthCheckConfig healthCheckConfig = thingsConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));

        final TagsConfig tagsConfig = thingsConfig.getTagsConfig();
        final ActorRef persistenceStreamingActor = startChildActor(ThingsPersistenceStreamingActorCreator.ACTOR_NAME,
                ThingsPersistenceStreamingActorCreator.props(tagsConfig.getStreamingCacheSize()));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(persistenceStreamingActor), getSelf());

        final HttpConfig httpConfig = thingsConfig.getHttpConfig();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }
        final CompletionStage<ServerBinding> binding = Http.get(actorSystem)
                .bindAndHandle(
                        createRoute(actorSystem, healthCheckingActor).flow(actorSystem,
                                materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint ...");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        );
        binding.thenAccept(this::logServerBinding)
                .exceptionally(failure -> {
                    log.error(failure, "Something very bad happened: {}", failure.getMessage());
                    actorSystem.terminate();
                    return null;
                });
    }

    /**
     * Creates Akka configuration object Props for this ThingsRootActor.
     *
     * @param thingsConfig the configuration settings of the Things service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the Akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return Props.create(ThingsRootActor.class, thingsConfig, pubSubMediator, materializer, thingSnapshotterCreate);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the things shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void logServerBinding(final ServerBinding serverBinding) {
        log.info("Bound to address {}:{}", serverBinding.localAddress().getHostString(),
                serverBinding.localAddress().getPort());
    }

    private static Props getThingSupervisorActorProps(final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return ThingSupervisorActor.props(pubSubMediator,
                ThingPersistenceActorPropsFactory.getInstance(pubSubMediator, thingSnapshotterCreate));
    }

}
