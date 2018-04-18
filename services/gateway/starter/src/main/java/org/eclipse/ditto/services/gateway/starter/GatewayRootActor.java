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
import java.time.Duration;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.endpoints.routes.RootRoute;
import org.eclipse.ditto.services.gateway.proxy.actors.AclEnforcerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.EnforcerLookupActor;
import org.eclipse.ditto.services.gateway.proxy.actors.PolicyEnforcerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.ProxyActor;
import org.eclipse.ditto.services.gateway.proxy.actors.ThingEnforcerLookupFunction;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.CommandRouterPropsFactory;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
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
import akka.dispatch.MessageDispatcher;
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
import scala.concurrent.duration.FiniteDuration;

/**
 * The Root Actor of the API Gateway's Akka ActorSystem.
 */
final class GatewayRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "gatewayRoot";

    private static final String GATEWAY_CLUSTER_ROLE = "gateway";

    private static final String ACL_ENFORCER_SHARD_REGION = "aclEnforcer";
    private static final String POLICY_ENFORCER_SHARD_REGION = "policyEnforcer";
    private static final String CHILD_RESTART_INFO_MSG = "Restarting child...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder //
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(CHILD_RESTART_INFO_MSG);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
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

    private final ActorRef policyEnforcerShardRegion;
    private final ActorRef aclEnforcerShardRegion;

    private GatewayRootActor(final Config config, final ActorRef pubSubMediator, final ActorMaterializer materializer) {

        final int numberOfShards = config.getInt(ConfigKeys.CLUSTER_NUMBER_OF_SHARDS);
        final ActorRef policiesShardRegionProxy = ClusterSharding.get(this.getContext().system())
                .startProxy(PoliciesMessagingConstants.SHARD_REGION,
                        Optional.of(PoliciesMessagingConstants.CLUSTER_ROLE), ShardRegionExtractor.of(numberOfShards,
                                getContext().getSystem()));
        final ActorRef thingsShardRegionProxy = ClusterSharding.get(this.getContext().system())
                .startProxy(ThingsMessagingConstants.SHARD_REGION,
                        Optional.of(ThingsMessagingConstants.CLUSTER_ROLE), ShardRegionExtractor.of(numberOfShards,
                                getContext().getSystem()));

        ClusterSharding.get(this.getContext().system())
                .startProxy(ThingsSearchConstants.SHARD_REGION, Optional.of(ThingsSearchConstants.CLUSTER_ROLE),
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final FiniteDuration enforcerCacheInterval = toFiniteDuration(config.getDuration(ConfigKeys
                .ENFORCER_CACHE_INTERVAL));
        final FiniteDuration enforcerInternalAskTimeout = toFiniteDuration(config.getDuration(ConfigKeys
                .ENFORCER_INTERNAL_ASK_TIMEOUT));

        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(this.getContext().system()).withRole(GATEWAY_CLUSTER_ROLE);

        final ActorRef thingCacheFacade = startChildActor(CacheFacadeActor.actorNameFor(CacheRole.THING),
                CacheFacadeActor.props(CacheRole.THING, config));

        final Props aclEnforcerProps =
                AclEnforcerActor.props(pubSubMediator, thingsShardRegionProxy, policiesShardRegionProxy,
                        thingCacheFacade, enforcerCacheInterval, enforcerInternalAskTimeout,
                        Collections.singletonList(SubjectIssuer.GOOGLE));
        aclEnforcerShardRegion = ClusterSharding.get(this.getContext().system())
                .start(ACL_ENFORCER_SHARD_REGION, aclEnforcerProps, shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final ActorRef policyCacheFacade = startChildActor(CacheFacadeActor.actorNameFor(CacheRole.POLICY),
                CacheFacadeActor.props(CacheRole.POLICY, config));
        final Props policyEnforcerProps =
                PolicyEnforcerActor.props(pubSubMediator, policiesShardRegionProxy, thingsShardRegionProxy,
                        policyCacheFacade, enforcerCacheInterval, enforcerInternalAskTimeout);
        policyEnforcerShardRegion = ClusterSharding.get(this.getContext().system())
                .start(POLICY_ENFORCER_SHARD_REGION, policyEnforcerProps, shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final MessageDispatcher lookupDispatcher =
                getContext().system().dispatchers().lookup("enforcer-lookup-dispatcher");

        final ThingEnforcerLookupFunction thingEnforcerLookupFunction =
                ThingEnforcerLookupFunction.of(thingsShardRegionProxy, aclEnforcerShardRegion,
                        policyEnforcerShardRegion, lookupDispatcher);
        final ActorRef thingEnforcerLookupActor = startChildActor(EnforcerLookupActor.actorNameFor(CacheRole.THING),
                EnforcerLookupActor.props(aclEnforcerShardRegion, policyEnforcerShardRegion, thingCacheFacade,
                        thingEnforcerLookupFunction));

        final ActorRef devOpsCommandsActor = startChildActor(DevOpsCommandsActor.ACTOR_NAME,
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), GatewayService.SERVICE_NAME,
                        ConfigUtil.instanceIndex()));

        final ActorRef proxyActor = startChildActor(ProxyActor.ACTOR_NAME,
                ProxyActor.props(pubSubMediator, devOpsCommandsActor, aclEnforcerShardRegion, policyEnforcerShardRegion,
                        thingEnforcerLookupActor, thingCacheFacade));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(proxyActor), getSelf());

        final Props commandRouterProps = CommandRouterPropsFactory.getProps(config);
        final ActorRef commandRouter = startChildActor("commandRouter", commandRouterProps);

        final ActorRef streamingActor = startChildActor(StreamingActor.ACTOR_NAME,
                StreamingActor.props(pubSubMediator, commandRouter));

        final ActorRef healthCheckActor = createHealthCheckActor(config);

        String hostname = config.getString(ConfigKeys.HTTP_HOSTNAME);
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system())
                .bindAndHandle(createRoute(getContext().system(), config, proxyActor, streamingActor, healthCheckActor)
                                .flow(getContext().system(), materializer),
                        ConnectHttp.toHost(hostname, config.getInt(ConfigKeys.HTTP_PORT)), materializer);

        binding.exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            getContext().system().terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param config the configuration settings of the API Gateway.
     * @param pubSubMediator the pub-sub mediator.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    static Props props(final Config config, final ActorRef pubSubMediator, final ActorMaterializer materializer) {
        return Props.create(GatewayRootActor.class, new Creator<GatewayRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public GatewayRootActor create() throws Exception {
                return new GatewayRootActor(config, pubSubMediator, materializer);
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
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState -> {
                    aclEnforcerShardRegion.forward(getShardRegionState, getContext());
                    policyEnforcerShardRegion.forward(getShardRegionState, getContext());
                })
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

    private ActorRef createHealthCheckActor(final Config config) {
        final HealthCheckingActorOptions.Builder hcBuilder = HealthCheckingActorOptions
                .getBuilder(config.getBoolean(ConfigKeys.HEALTH_CHECK_ENABLED),
                        config.getDuration(ConfigKeys.HEALTH_CHECK_INTERVAL));

        if (config.getBoolean(ConfigKeys.HEALTH_CHECK_PERSISTENCE_ENABLED)) {
            hcBuilder.enablePersistenceCheck();
        }

        final ActorRef mongoClient = startChildActor(MongoClientActor.ACTOR_NAME, MongoClientActor
                .props(config.getString(ConfigKeys.MONGO_URI),
                        config.getDuration(ConfigKeys.HEALTH_CHECK_PERSISTENCE_TIMEOUT)));

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, mongoClient));
    }

    private FiniteDuration toFiniteDuration(final Duration duration) {
        return scala.concurrent.duration.FiniteDuration.apply(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

}
