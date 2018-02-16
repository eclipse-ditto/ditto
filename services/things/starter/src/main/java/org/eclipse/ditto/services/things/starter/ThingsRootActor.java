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
package org.eclipse.ditto.services.things.starter;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.things.persistence.actors.ThingsPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientActor;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
final class ThingsRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsRoot";

    private static final String RESTARTING_CHILD_MESSAGE = "Restarting child...";

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
                        "DittoRuntimeException '{}' should not be escalated to ThingsRootActor. Simply resuming Actor.",
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

    private final ActorRef thingsShardRegion;


    private ThingsRootActor(final Config config,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final Function<ActorRef, Props> supervisorActorPropsFactory) {

        final int numberOfShards = config.getInt(ConfigKeys.Cluster.NUMBER_OF_SHARDS);

        final ActorRef thingCacheFacade = startChildActor(CacheFacadeActor.actorNameFor(CacheRole.THING),
                CacheFacadeActor.props(CacheRole.THING, config));

        final Props thingSupervisorProps = supervisorActorPropsFactory.apply(thingCacheFacade);

        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(getContext().system())
                        .withRole(ThingsMessagingConstants.CLUSTER_ROLE);

        thingsShardRegion = ClusterSharding.get(getContext().system())
                .start(ThingsMessagingConstants.SHARD_REGION,
                        thingSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final boolean healthCheckEnabled = config.getBoolean(ConfigKeys.HealthCheck.ENABLED);
        final Duration healthCheckInterval = config.getDuration(ConfigKeys.HealthCheck.INTERVAL);

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckEnabled, healthCheckInterval);
        if (config.getBoolean(ConfigKeys.HealthCheck.PERSISTENCE_ENABLED)) {
            hcBuilder.enablePersistenceCheck();
        }

        final ActorRef mongoClient = startChildActor(MongoClientActor.ACTOR_NAME, MongoClientActor
                .props(config.getString(ConfigKeys.MONGO_URI),
                        config.getDuration(ConfigKeys.HealthCheck.PERSISTENCE_TIMEOUT)));

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, mongoClient));

        final int tagsStreamingCacheSize = config.getInt(ConfigKeys.THINGS_TAGS_STREAMING_CACHE_SIZE);
        final ActorRef persistenceStreamingActor = startChildActor(ThingsPersistenceStreamingActorCreator.ACTOR_NAME,
                ThingsPersistenceStreamingActorCreator.props(config, tagsStreamingCacheSize));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(persistenceStreamingActor), getSelf());

        String hostname = config.getString(ConfigKeys.Http.HOSTNAME);
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }
        final CompletionStage<ServerBinding> binding = Http.get(getContext().system()) //
                .bindAndHandle(
                        createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                                materializer),
                        ConnectHttp.toHost(hostname, config.getInt(ConfigKeys.Http.PORT)), materializer);
        binding.thenAccept(this::logServerBinding)
                .exceptionally(failure -> {
                    log.error(failure, "Something very bad happened: {}", failure.getMessage());
                    getContext().system().terminate();
                    return null;
                });
    }

    /**
     * Creates Akka configuration object Props for this ThingsRootActor.
     *
     * @param config the configuration settings of the Things Service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @param supervisorActorPropsFactory factory for creating actor Props of the {@code ThingSupervisorActor}.
     * @return the Akka configuration Props object.
     */
    static Props props(final Config config,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final Function<ActorRef, Props> supervisorActorPropsFactory) {

        return Props.create(ThingsRootActor.class, new Creator<ThingsRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsRootActor create() throws Exception {
                return new ThingsRootActor(config, pubSubMediator, materializer, supervisorActorPropsFactory);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        thingsShardRegion.forward(getShardRegionState, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

    private void logServerBinding(final ServerBinding serverBinding) {
        log.info("Bound to address {}:{}", serverBinding.localAddress().getHostString(),
                serverBinding.localAddress().getPort());
    }
}
