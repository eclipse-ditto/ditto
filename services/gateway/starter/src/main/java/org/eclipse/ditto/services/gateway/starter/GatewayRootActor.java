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
package org.eclipse.ditto.services.gateway.starter;

import java.net.ConnectException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.HealthConfigReader;
import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.gateway.endpoints.routes.RootRoute;
import org.eclipse.ditto.services.gateway.proxy.actors.ProxyActor;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;

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
 * The Root Actor of the API Gateway's Akka ActorSystem.
 */
final class GatewayRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "gatewayRoot";

    private static final String CHILD_RESTART_INFO_MSG = "Restarting child...";

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
                        "DittoRuntimeException '{}' should not be escalated to GatewayRootActor. Simply resuming Actor.",
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
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private GatewayRootActor(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        final int numberOfShards = configReader.cluster().numberOfShards();
        final Config config = configReader.getRawConfig();

        final ActorSystem actorSystem = context().system();

        // start the cluster sharding proxies for retrieving Statistics via StatisticActor about them:
        ClusterSharding.get(actorSystem)
                .startProxy(PoliciesMessagingConstants.SHARD_REGION, Optional.of(PoliciesMessagingConstants.CLUSTER_ROLE),
                        ShardRegionExtractor.of(numberOfShards, actorSystem));
        ClusterSharding.get(actorSystem)
                .startProxy(ThingsMessagingConstants.SHARD_REGION, Optional.of(ThingsMessagingConstants.CLUSTER_ROLE),
                        ShardRegionExtractor.of(numberOfShards, actorSystem));
        ClusterSharding.get(actorSystem)
                .startProxy(ThingsSearchConstants.SHARD_REGION, Optional.of(ThingsSearchConstants.CLUSTER_ROLE),
                        ShardRegionExtractor.of(numberOfShards, actorSystem));

        final ActorRef devOpsCommandsActor = startChildActor(DevOpsCommandsActor.ACTOR_NAME,
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), GatewayService.SERVICE_NAME,
                        ConfigUtil.instanceIndex()));

        final ActorRef conciergeShardRegionProxy = ClusterSharding.get(actorSystem)
                .startProxy(ConciergeMessagingConstants.SHARD_REGION,
                        Optional.of(ConciergeMessagingConstants.CLUSTER_ROLE),
                        ShardRegionExtractor.of(numberOfShards, actorSystem));

        final ActorRef conciergeForwarder = startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeShardRegionProxy));

        final ActorRef proxyActor = startChildActor(ProxyActor.ACTOR_NAME,
                ProxyActor.props(pubSubMediator, devOpsCommandsActor, conciergeForwarder));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final ActorRef streamingActor = startChildActor(StreamingActor.ACTOR_NAME,
                StreamingActor.props(pubSubMediator, proxyActor));

        final HealthConfigReader healthConfig = configReader.health();
        final ActorRef healthCheckActor = createHealthCheckActor(healthConfig);

        final HttpConfigReader httpConfig = configReader.http();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(actorSystem)
                .bindAndHandle(createRoute(actorSystem, config, proxyActor, streamingActor, healthCheckActor)
                                .flow(actorSystem, materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            actorSystem.terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the pub-sub mediator.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    static Props props(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        return Props.create(GatewayRootActor.class, new Creator<GatewayRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public GatewayRootActor create() {
                return new GatewayRootActor(configReader, pubSubMediator, materializer);
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

    private Route createRoute(final ActorSystem actorSystem, final Config config,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor) {

        final HttpClientFacade httpClient = HttpClientFacade.getInstance(actorSystem);
        final RootRoute rootRoute = new RootRoute(actorSystem, config,
                proxyActor,
                streamingActor,
                healthCheckingActor,
                new ClusterStatusSupplier(Cluster.get(actorSystem)),
                httpClient);

        return rootRoute.buildRoute();
    }

    private ActorRef createHealthCheckActor(final HealthConfigReader healthConfig) {
        final HealthCheckingActorOptions.Builder hcBuilder = HealthCheckingActorOptions
                .getBuilder(healthConfig.enabled(),
                        healthConfig.getInterval());

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, null));
    }

}
