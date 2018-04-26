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
package org.eclipse.ditto.services.connectivity.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.connectivity.messaging.ConnectionActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ConnectionSupervisorActor;
import org.eclipse.ditto.services.connectivity.messaging.DefaultConnectionActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ReconnectActor;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
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
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
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

    private ConnectivityRootActor(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
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

        final Duration minBackoff = config.getDuration(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackoff = config.getDuration(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor =
                config.getDouble(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);
        final ConnectionActorPropsFactory propsFactory = DefaultConnectionActorPropsFactory.getInstance();
        final Props connectionSupervisorProps =
                ConnectionSupervisorActor.props(minBackoff, maxBackoff, randomFactor, pubSubMediator, propsFactory);

        final int numberOfShards = config.getInt(ConfigKeys.Cluster.NUMBER_OF_SHARDS);
        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(this.getContext().system())
                        .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE);

        final ActorRef connectionShardRegion = ClusterSharding.get(this.getContext().system())
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        startClusterSingletonActor(ReconnectActor.ACTOR_NAME,
                ReconnectActor.props(connectionShardRegion, pubSubMediator));

        String hostname = config.getString(ConfigKeys.Http.HOSTNAME);
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system()).bindAndHandle( //
                createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(), materializer),
                ConnectHttp.toHost(hostname, config.getInt(ConfigKeys.Http.PORT)),
                materializer);

        binding.exceptionally(failure ->
        {
            log.error("Something very bad happened! " + failure.getMessage(), failure);
            getContext().system().terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param config the configuration settings of the Things Service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        return Props.create(ConnectivityRootActor.class, new Creator<ConnectivityRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectivityRootActor create() {
                return new ConnectivityRootActor(config, pubSubMediator, materializer);
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

    private SupervisorStrategy.Directive restartChild() {
        log.info("Restarting child ...");
        return SupervisorStrategy.restart();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void startClusterSingletonActor(final String actorName, final Props props) {
        final ClusterSingletonManagerSettings settings =
                ClusterSingletonManagerSettings.create(getContext().system()).withRole(CLUSTER_ROLE);
        getContext().actorOf(ClusterSingletonManager.props(props, PoisonPill.getInstance(), settings), actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

}
