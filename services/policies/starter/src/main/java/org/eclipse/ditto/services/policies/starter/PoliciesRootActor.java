/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.starter;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.base.config.HealthConfigReader;
import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.policies.persistence.actors.policies.PoliciesPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicySupervisorActor;
import org.eclipse.ditto.services.policies.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientActor;

import com.typesafe.config.Config;

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
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class PoliciesRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "policiesRoot";

    private static final String RESTARTING_CHILD_MESSAGE = "Restarting child...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MESSAGE);
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
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to PoliciesRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final ActorRef policiesShardRegion;

    private PoliciesRootActor(final ServiceConfigReader configReader,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        final int numberOfShards = configReader.cluster().numberOfShards();
        final Config config = configReader.getRawConfig();

        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(getContext().system()).withRole(PoliciesMessagingConstants.CLUSTER_ROLE);

        final Duration minBackoff = config.getDuration(ConfigKeys.Policy.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackoff = config.getDuration(ConfigKeys.Policy.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor = config.getDouble(ConfigKeys.Policy.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);
        final Props policySupervisorProps = PolicySupervisorActor.props(pubSubMediator, minBackoff, maxBackoff,
                randomFactor, snapshotAdapter);

        final int tagsStreamingCacheSize = config.getInt(ConfigKeys.POLICIES_TAGS_STREAMING_CACHE_SIZE);
        final ActorRef persistenceStreamingActor = startChildActor(PoliciesPersistenceStreamingActorCreator.ACTOR_NAME,
                PoliciesPersistenceStreamingActorCreator.props(config, tagsStreamingCacheSize));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(persistenceStreamingActor), getSelf());

        policiesShardRegion = ClusterSharding.get(getContext().system())
                .start(PoliciesMessagingConstants.SHARD_REGION, policySupervisorProps, shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final HealthConfigReader healthConfig = configReader.health();

        final ActorRef mongoClient = startChildActor(MongoClientActor.ACTOR_NAME, MongoClientActor
                .props(config.getString(ConfigKeys.MONGO_URI),
                        healthConfig.getPersistenceTimeout(),
                        MongoConfig.getSSLEnabled(config)));

        final boolean healthCheckEnabled = healthConfig.enabled();
        final Duration healthCheckInterval = healthConfig.getInterval();

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckEnabled, healthCheckInterval);
        if (healthConfig.persistenceEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final Props healthCheckingActorProps =
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, mongoClient);
        final ActorRef healthCheckingActor =
                startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME, healthCheckingActorProps);

        final HttpConfigReader httpConfig = configReader.http();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
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
        );
        binding.thenAccept(this::logServerBinding)
                .exceptionally(failure -> {
                    log.error(failure, "Something very bad happened: {}", failure.getMessage());
                    getContext().system().terminate();
                    return null;
                });
    }

    /**
     * Creates Akka configuration object Props for this PoliciesRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param snapshotAdapter serializer and deserializer of the Policies snapshot store.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ServiceConfigReader configReader,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return Props.create(PoliciesRootActor.class, new Creator<PoliciesRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PoliciesRootActor create() {
                return new PoliciesRootActor(configReader, snapshotAdapter, pubSubMediator, materializer);
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
                        policiesShardRegion.forward(getShardRegionState, getContext()))
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
